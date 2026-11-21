/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;
import static com.android.launcher3.config.FeatureFlags.shouldShowFirstPageWidget;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;

import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;

import java.util.ArrayList;
import java.util.HashSet;

/** Fills empty hotseat and workspace cells with launchable activities missing from the layout. */
class SyncWorkspaceAppsTask extends BaseModelUpdateTask {
    @Override
    public void execute(@NonNull LauncherAppState app, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList apps) {
        InvariantDeviceProfile idp = app.getInvariantDeviceProfile();
        ModelWriter writer = getModelWriter();
        IntArray newScreens = new IntArray();
        ArrayList<ItemInfo> added = new ArrayList<>();
        synchronized (dataModel) {
            HashSet<ComponentKey> placed = new HashSet<>();
            int hotseatSize = idp.supportedProfiles.stream().mapToInt(dp -> dp.numShownHotseatIcons)
                    .min().orElse(idp.numShownHotseatIcons);
            GridOccupancy hotseat = new GridOccupancy(hotseatSize, 1);
            SparseArray<GridOccupancy> pages = new SparseArray<>();
            for (int screen : dataModel.collectWorkspaceScreens()) {
                pages.put(screen, new GridOccupancy(idp.numColumns, idp.numRows));
            }
            if (dataModel.isFirstPagePinnedItemEnabled && !shouldShowFirstPageWidget()) {
                pages.get(FIRST_SCREEN_ID).markCells(0, 0, idp.numSearchContainerColumns, 1, true);
            }
            for (ItemInfo item : dataModel.itemsIdMap) {
                if (item.container == CONTAINER_HOTSEAT) {
                    hotseat.markCells(item.screenId, 0, 1, 1, true);
                } else if (item.container == CONTAINER_DESKTOP) {
                    pages.get(item.screenId).markCells(item, true);
                }
                if (isPlacedApp(dataModel, item)) {
                    placed.add(item.getComponentKey());
                }
            }

            int page = 0;
            int[] cell = new int[2];
            for (AppInfo activity : apps.data) {
                // Install-session placeholders have no activity class yet.
                if (activity.componentName == null || activity.componentName.getClassName().isEmpty()
                        || (activity.runtimeStatusFlags & FLAG_NOT_PINNABLE) != 0
                        || !placed.add(activity.getComponentKey())) {
                    continue;
                }
                WorkspaceItemInfo icon = activity.makeWorkspaceItem(app.getContext());
                if (hotseat.findVacantCell(cell, 1, 1)) {
                    hotseat.markCells(cell[0], 0, 1, 1, true);
                    writer.addItemToDatabase(icon, CONTAINER_HOTSEAT, cell[0], cell[0], 0);
                } else {
                    while (page < pages.size() && !pages.valueAt(page).findVacantCell(cell, 1, 1)) {
                        page++;
                    }
                    if (page == pages.size()) {
                        int screen = app.getModel().getModelDbController().getNewScreenId();
                        pages.append(screen, new GridOccupancy(idp.numColumns, idp.numRows));
                        newScreens.add(screen);
                        cell[0] = cell[1] = 0;
                    }
                    pages.valueAt(page).markCells(cell[0], cell[1], 1, 1, true);
                    writer.addItemToDatabase(icon, CONTAINER_DESKTOP, pages.keyAt(page),
                            cell[0], cell[1]);
                }
                added.add(icon);
            }
        }
        if (!added.isEmpty()) {
            scheduleCallbackTask(c -> c.bindAppsAdded(newScreens.clone(), added, new ArrayList<>()));
        }
    }

    static boolean isPlacedApp(BgDataModel model, ItemInfo item) {
        if (item.itemType != ITEM_TYPE_APPLICATION) return false;
        if (item.container == CONTAINER_DESKTOP || item.container == CONTAINER_HOTSEAT) return true;
        FolderInfo folder = model.folders.get(item.container);
        return folder != null && folder.itemType == ITEM_TYPE_FOLDER;
    }
}
