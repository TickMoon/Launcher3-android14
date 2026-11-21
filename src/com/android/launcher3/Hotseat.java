/*
 * Copyright (C) 2011 The Android Open Source Project
 *
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

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.views.ActivityContext;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable {

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private final IntSet mOccupiedSlots = new IntSet();
    private IntSet mDragSlots;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;

    private final View mQsb;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);
        addView(mQsb);
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    private int axis(int cellX, int cellY) {
        return mHasVerticalHotseat ? cellY : cellX;
    }

    private IntSet getSlots() {
        if (mDragSlots != null) return mDragSlots;
        mOccupiedSlots.clear();
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            mOccupiedSlots.add(axis(lp.getCellX(), lp.getCellY()));
        }
        return mOccupiedSlots;
    }

    /** Freeze the displayed slots so moving a neighbour does not move the pointer's target. */
    void beginDragOver(View source) {
        if (mDragSlots != null) return;
        IntSet slots = new IntSet().addAll(getSlots());
        if (source == null || source.getParent() != getShortcutsAndWidgets()) {
            int capacity = axis(getCountX(), getCountY());
            for (int i = 0; i < capacity; i++) {
                if (!slots.contains(i)) {
                    slots.add(i);
                    break;
                }
            }
        }
        mDragSlots = slots;
        getShortcutsAndWidgets().requestLayout();
    }

    void endDragOver() {
        if (mDragSlots == null) return;
        mDragSlots = null;
        getShortcutsAndWidgets().requestLayout();
    }

    /** Give each occupied rank an equal share of the icon area, keeping its model rank intact. */
    int getIconSlotOffset(int cellX, int cellY) {
        int length = getSlotAreaLength();
        int cell = axis(cellX, cellY);
        IntArray slots = getSlots().getArray();
        int index = slots.indexOf(cell);
        if (length <= 0 || index < 0) return 0;
        int cellSize = mHasVerticalHotseat ? getCellHeight() : getCellWidth();
        int spacing = mHasVerticalHotseat ? mBorderSpace.y : mBorderSpace.x;
        return Math.round((index + 0.5f) * length / slots.size() - cellSize / 2f)
                - cell * (cellSize + spacing);
    }

    private int getSlotAreaLength() {
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        if (mHasVerticalHotseat) return icons.getMeasuredHeight();
        // CellLayout trims the unused pixels from both sides when laying out this container.
        int width = icons.getMeasuredWidth();
        int gridWidth = getCountX() * getCellWidth() + (getCountX() - 1) * mBorderSpace.x;
        return width - 2 * (int) Math.ceil((width - gridWidth) / 2f);
    }

    @Override
    public void pointToCellExact(int x, int y, int[] result) {
        IntArray slots = getSlots().getArray();
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        int length = getSlotAreaLength();
        if (slots.isEmpty() || length <= 0) {
            super.pointToCellExact(x, y, result);
            return;
        }
        int position = axis(x - icons.getLeft(), y - icons.getTop());
        int index = Utilities.boundToRange(position * slots.size() / length, 0, slots.size() - 1);
        int slot = slots.get(index);
        result[0] = mHasVerticalHotseat ? 0 : slot;
        result[1] = mHasVerticalHotseat ? slot : 0;
    }

    @Override
    public int[] findNearestAreaIgnoreOccupied(int x, int y, int spanX, int spanY, int[] result) {
        if (mDragSlots == null || spanX != 1 || spanY != 1) {
            return super.findNearestAreaIgnoreOccupied(x, y, spanX, spanY, result);
        }
        if (result == null) result = new int[2];
        pointToCellExact(x, y, result);
        return result;
    }

    @Override
    public ItemConfiguration calculateReorder(int x, int y, int minSpanX, int minSpanY,
            int spanX, int spanY, View dragView) {
        if (mDragSlots == null || spanX != 1 || spanY != 1) {
            return super.calculateReorder(x, y, minSpanX, minSpanY, spanX, spanY, dragView);
        }
        ItemConfiguration solution = new ItemConfiguration();
        copyCurrentStateToSolution(solution);
        solution.sortedViews.remove(dragView);
        IntArray slots = mDragSlots.getArray();
        if (slots.size() != solution.sortedViews.size() + 1) return solution;

        int[] target = findNearestAreaIgnoreOccupied(x, y, 1, 1, null);
        int dropSlot = axis(target[0], target[1]);
        // Insert at the displayed slot, retaining the relative order and any holes in model ranks.
        solution.sortedViews.sort((a, b) -> {
            CellAndSpan first = solution.map.get(a);
            CellAndSpan second = solution.map.get(b);
            return Integer.compare(axis(first.cellX, first.cellY), axis(second.cellX, second.cellY));
        });
        int index = 0;
        for (View child : solution.sortedViews) {
            if (slots.get(index) == dropSlot) index++;
            int slot = slots.get(index++);
            CellAndSpan cell = solution.map.get(child);
            if (axis(cell.cellX, cell.cellY) != slot) {
                if (!((CellLayoutLayoutParams) child.getLayoutParams()).canReorder) return solution;
                solution.intersectingViews.add(child);
                cell.cellX = mHasVerticalHotseat ? 0 : slot;
                cell.cellY = mHasVerticalHotseat ? slot : 0;
            }
        }
        solution.cellX = target[0];
        solution.cellY = target[1];
        solution.isSolution = true;
        return solution;
    }

    @Override
    public void cellToRect(int cellX, int cellY, int spanX, int spanY, Rect bounds) {
        super.cellToRect(cellX, cellY, spanX, spanY, bounds);
        int offset = getIconSlotOffset(cellX, cellY);
        bounds.offset(mHasVerticalHotseat ? 0 : offset, mHasVerticalHotseat ? offset : 0);
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        if (bubbleBarEnabled) {
            float adjustedBorderSpace = dp.getHotseatAdjustedBorderSpaceForBubbleBar(getContext());
            if (hasBubbles && Float.compare(adjustedBorderSpace, 0f) != 0) {
                getShortcutsAndWidgets().setTranslationProvider(child -> {
                    int index = getShortcutsAndWidgets().indexOfChild(child);
                    float borderSpaceDelta = adjustedBorderSpace - dp.hotseatBorderSpace;
                    return dp.iconSizePx + index * borderSpaceDelta;
                });
                if (mQsb instanceof HorizontalInsettableView) {
                    HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                    final float insetFraction = (float) dp.iconSizePx / dp.hotseatQsbWidth;
                    // post this to the looper so that QSB has a chance to redraw itself, e.g.
                    // after device rotation
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                }
            } else {
                getShortcutsAndWidgets().setTranslationProvider(null);
                if (mQsb instanceof HorizontalInsettableView) {
                    ((HorizontalInsettableView) mQsb).setHorizontalInsets(0);
                }
            }
        }

        resetCellSize(dp);
        if (hasVerticalHotseat) {
            setGridSize(1, dp.numShownHotseatIcons);
        } else {
            setGridSize(dp.numShownHotseatIcons, 1);
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     *
     * <p>When the bubble bar becomes visible, if needed, this method animates the hotseat icons
     * to reduce the spacing between them and make room for the bubble bar. The QSB width is
     * animated as well to align with the hotseat icons.
     *
     * <p>When the bubble bar goes away, any adjustments that were previously made are reversed.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        float adjustedBorderSpace = dp.getHotseatAdjustedBorderSpaceForBubbleBar(getContext());
        if (Float.compare(adjustedBorderSpace, 0f) == 0) {
            return;
        }

        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        AnimatorSet animatorSet = new AnimatorSet();
        float borderSpaceDelta = adjustedBorderSpace - dp.hotseatBorderSpace;

        // update the translation provider for future layout passes of hotseat icons.
        if (isBubbleBarVisible) {
            icons.setTranslationProvider(child -> {
                int index = icons.indexOfChild(child);
                return dp.iconSizePx + index * borderSpaceDelta;
            });
        } else {
            icons.setTranslationProvider(null);
        }

        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            float tx = isBubbleBarVisible ? dp.iconSizePx + i * borderSpaceDelta : 0;
            if (child instanceof Reorderable) {
                MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                animatorSet.play(
                        mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
            } else {
                animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
            }
        }
        if (mQsb instanceof HorizontalInsettableView) {
            HorizontalInsettableView horizontalInsettableQsb = (HorizontalInsettableView) mQsb;
            ValueAnimator qsbAnimator = ValueAnimator.ofFloat(0f, 1f);
            qsbAnimator.addUpdateListener(animation -> {
                float fraction = qsbAnimator.getAnimatedFraction();
                float insetFraction = isBubbleBarVisible
                        ? (float) dp.iconSizePx * fraction / dp.hotseatQsbWidth
                        : (float) dp.iconSizePx * (1 - fraction) / dp.hotseatQsbWidth;
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            mQsb.setVisibility(View.VISIBLE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx;
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        setCellLayoutContainer(w);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        // Always let touch follow through to Workspace.
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        DeviceProfile dp = mActivity.getDeviceProfile();
        mQsb.measure(MeasureSpec.makeMeasureSpec(dp.hotseatQsbWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp.hotseatQsbHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int left;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (dp.isQsbInline) {
            int qsbSpace = dp.hotseatBorderSpace;
            left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                    : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
        } else {
            left = (r - l - qsbMeasuredWidth) / 2;
        }
        int right = left + qsbMeasuredWidth;

        int bottom = b - t - dp.getQsbOffsetY();
        int top = bottom - dp.hotseatQsbHeight;
        mQsb.layout(left, top, right, bottom);
    }

    /**
     * Sets the alpha value of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha) {
        getShortcutsAndWidgets().setAlpha(alpha);
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha) {
        mQsb.setAlpha(alpha);
    }

    public float getIconsAlpha() {
        return getShortcutsAndWidgets().getAlpha();
    }

    /**
     * Returns the QSB inside hotseat
     */
    public View getQsb() {
        return mQsb;
    }

}
