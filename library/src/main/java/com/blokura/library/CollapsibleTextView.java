package com.blokura.library;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DimenRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.content.res.AppCompatResources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

public class CollapsibleTextView extends LinearLayout implements View.OnClickListener {

    private static final float DEFAULT_ANIM_ALPHA_START = 0.7f;
    private static final int DEFAULT_ANIM_DURATION = 300;
    private static final int DEFAULT_VISIBLE_LINES = 4;
    private static final float ALPHA_OPAQUE = 1f;
    private static final int ALPHA_TRANSPARENT = 0;
    public static final int MIN_VISIBLE_LINES = 1;
    private MarginUpdateRunnable marginUpdateRunnable = new MarginUpdateRunnable(this);

    //region UI
    /**
     * Contains the text
     */
    private TextView tvBody;

    /**
     * Expands or collapses the view
     */
    private TextView tvExpand;

    /**
     * Gradient at the end of the text
     */
    private ImageView ivGradient;
    //endregion

    //region STATE
    private boolean isCollapsed = true;
    private boolean isAnimating;
    //endregion

    //region EXPAND BUTTON DECORATION
    private String viewMoreLabel;
    private String viewLessLabel;
    private Drawable expandIcon;
    private Drawable collapseIcon;
    private Drawable transitionGradient;
    private boolean showIcon = true;

    @ColorInt
    private int expandCollapseIconTint;
    //endregion

    //region CONFIGURATION
    private int collapsedHeight;
    private int textHeightWithMaxLines;
    private int marginBetweenTextAndBottom;
    private int visibleLineCount;
    private long animationDurationMillis;

    @FloatRange(from = 0.0, to = 1.0)
    private float animAlphaStart;
    //endregion

    //region CALLBACKS
    private OnExpandStateChangedListener listener;
    //endregion

    //region FLAGS
    private boolean hasChanged;
    //endregion

    public CollapsibleTextView(Context context) {
        this(context, null);
    }
    //endregion

    public CollapsibleTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CollapsibleTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            init(attrs);
        }
    }

    //region INITIALIZATION
    private void init(AttributeSet attrs) {
        inflate(getContext(), R.layout.ctv_collapsible, this);
        setOrientation(LinearLayout.VERTICAL);
        bindViews();
        loadStyle(attrs);

        //Default visibility is GONE
        setVisibility(GONE);
    }

    private void bindViews() {
        tvBody = (TextView) findViewById(R.id.ctv_tv_body);
        tvExpand = (TextView) findViewById(R.id.ctv_bt_collapse);
        ivGradient = (ImageView) findViewById(R.id.ctv_iv_gradient);
    }

    private void loadStyle(final AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.CollapsibleTextView);
            if (typedArray != null) {
                visibleLineCount = Math.max(MIN_VISIBLE_LINES,
                                            typedArray.getInt(R.styleable.CollapsibleTextView_ctv_visibleLinesCount,
                                                              DEFAULT_VISIBLE_LINES));

                loadCollapseChangeAnimation(typedArray);
                loadBodyStyle(typedArray);
                loadExpandCollapseButtonStyle(typedArray);
                loadTransitionStyle(typedArray);
                typedArray.recycle();
            }
        }
    }

    private void loadCollapseChangeAnimation(TypedArray typedArray) {
        animationDurationMillis = Math.max(0, (long) typedArray.getInt(R.styleable.CollapsibleTextView_ctv_animDuration,
                                                                       DEFAULT_ANIM_DURATION));

        animAlphaStart = sanitizeAlpha(
            typedArray.getFloat(R.styleable.CollapsibleTextView_ctv_animAlphaStart, DEFAULT_ANIM_ALPHA_START));
    }

    @FloatRange(from = 0.0, to = 1.0)
    private float sanitizeAlpha(float rawAlpha) {
        if (rawAlpha < 0f || rawAlpha > 1f) {
            return DEFAULT_ANIM_ALPHA_START;
        }
        return rawAlpha;
    }

    private void loadBodyStyle(TypedArray typedArray) {
        final ColorStateList textColor =
            typedArray.getColorStateList(R.styleable.CollapsibleTextView_ctv_bodyTextColor);
        if (textColor != null) {
            tvBody.setTextColor(textColor);
        }

        final int textSize = typedArray.getDimensionPixelSize(R.styleable.CollapsibleTextView_ctv_bodyTextSize, -1);
        if (textSize != -1) {
            tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        }

        final String text = typedArray.getString(R.styleable.CollapsibleTextView_ctv_bodyText);
        if (text != null && !text.isEmpty()) {
            setText(text);
        }
    }

    private void loadExpandCollapseButtonStyle(TypedArray typedArray) {
        showIcon = typedArray.getBoolean(R.styleable.CollapsibleTextView_ctv_showIcon, true);
        if (showIcon) {
            expandCollapseIconTint = typedArray.getColor(R.styleable.CollapsibleTextView_ctv_expandCollapseIconTint, 0);
            @DrawableRes int expandDrawableRes =
                typedArray.getResourceId(R.styleable.CollapsibleTextView_ctv_expandIconDrawable, 0);
            @DrawableRes int collapseDrawableRes =
                typedArray.getResourceId(R.styleable.CollapsibleTextView_ctv_collapseIconDrawable, 0);

            expandIcon =
                expandDrawableRes == 0 ? AppCompatResources.getDrawable(getContext(), R.drawable.ctv_icv_arrow_down_24)
                    : AppCompatResources.getDrawable(getContext(), expandDrawableRes);
            collapseIcon =
                collapseDrawableRes == 0 ? AppCompatResources.getDrawable(getContext(), R.drawable.ctv_icv_arrow_up_24)
                    : AppCompatResources.getDrawable(getContext(), collapseDrawableRes);

            if (expandCollapseIconTint != 0) {
                if (expandIcon != null) {
                    Drawable wrappedExpand = DrawableCompat.wrap(expandIcon);
                    DrawableCompat.setTint(wrappedExpand, expandCollapseIconTint);
                }
                if (collapseIcon != null) {
                    Drawable wrappedExpand = DrawableCompat.wrap(collapseIcon);
                    DrawableCompat.setTint(wrappedExpand, expandCollapseIconTint);
                }
            }
        }

        viewLessLabel = typedArray.getString(R.styleable.CollapsibleTextView_ctv_collapseLabel);
        viewMoreLabel = typedArray.getString(R.styleable.CollapsibleTextView_ctv_expandLabel);

        final ColorStateList btnTextColor =
            typedArray.getColorStateList(R.styleable.CollapsibleTextView_ctv_expandCollapseLabelColor);
        if (btnTextColor != null) {
            tvExpand.setTextColor(btnTextColor);
        }

        final int buttonTextSize =
            typedArray.getDimensionPixelSize(R.styleable.CollapsibleTextView_ctv_expandCollapseLabelTextSize, -1);
        if (buttonTextSize != -1) {
            tvExpand.setTextSize(TypedValue.COMPLEX_UNIT_SP, buttonTextSize);
        }
    }

    private void loadTransitionStyle(TypedArray typedArray) {
        boolean showGradient = typedArray.getBoolean(R.styleable.CollapsibleTextView_ctv_showGradient, false);
        if (showGradient) {
            int transitionResourceId = typedArray.getResourceId(R.styleable.CollapsibleTextView_ctv_gradientDrawable,
                                                                R.drawable.ctv_transition_gradient_white);
            transitionGradient = AppCompatResources.getDrawable(getContext(), transitionResourceId);
            ivGradient.setImageDrawable(transitionGradient);
        }
    }
    //endregion

    //region LIFE CYCLE
    @Override
    public void setOrientation(final int orientation) {
        if (LinearLayout.HORIZONTAL == orientation) {
            throw new IllegalArgumentException("CollapsibleTextView only supports Vertical Orientation");
        }
        super.setOrientation(orientation);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        marginUpdateRunnable = new MarginUpdateRunnable(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        bindViews();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        // If no change, measure and return
        if (!hasChanged || getVisibility() == View.GONE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        hasChanged = false;

        // Initially we suppose that everything fits
        tvExpand.setVisibility(GONE);
        ivGradient.setVisibility(GONE);
        tvBody.setMaxLines(Integer.MAX_VALUE);
        setOnClickListener(null);
        setClickable(false);

        // Measure
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // If text fits in collapsed mode, we are done
        if (tvBody.getLineCount() <= visibleLineCount) {
            return;
        }

        // Save the text height with max lines
        textHeightWithMaxLines = getFullTextViewHeight(tvBody);

        setOnClickListener(this);
        setClickable(true);
        if (isCollapsed) {
            // Doesn't fit. Collapse text
            tvBody.setMaxLines(visibleLineCount);
            tvExpand.setVisibility(VISIBLE);
            ivGradient.setVisibility(VISIBLE);
        }

        // Re-measure with new data
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (isCollapsed) {
            tvBody.post(marginUpdateRunnable);
            // Save the collapsed height of this ViewGroup
            collapsedHeight = getMeasuredHeight();
        }
    }

    private int getFullTextViewHeight(@NonNull TextView textView) {
        final int lineCount = textView.getLayout().getLineCount();
        final int textHeight = textView.getLayout().getLineTop(lineCount);
        final int padding = textView.getCompoundPaddingTop() + textView.getCompoundPaddingBottom();
        return textHeight + padding;
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        return isAnimating;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearAnimation();
        marginUpdateRunnable.collapsibleTextViewWeakReference.clear();
    }
    //endregion

    //region CLICK CALLBACK
    @Override
    public void onClick(View v) {
        if (tvExpand.getVisibility() == View.VISIBLE) {
            toggleText();
        }
    }

    private void toggleText() {
        clearAnimation();
        isCollapsed = !isCollapsed;
        tvExpand.setText(isCollapsed ? viewMoreLabel : viewLessLabel);
        if (showIcon) {
            Drawable icon = isCollapsed ? expandIcon : collapseIcon;
            tvExpand.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
        }

        setAnimating(true);
        ValueAnimator valueAnimatorHeight;
        ValueAnimator valueAnimatorAlpha;
        ObjectAnimator gradientAnimator;
        if (isCollapsed) {
            valueAnimatorHeight = ValueAnimator.ofInt(getHeight(), collapsedHeight);
            valueAnimatorAlpha = ValueAnimator.ofFloat(animAlphaStart, ALPHA_OPAQUE);
            gradientAnimator = ObjectAnimator.ofFloat(ivGradient, "alpha", ALPHA_TRANSPARENT, ALPHA_OPAQUE);
        } else {
            valueAnimatorHeight =
                ValueAnimator.ofInt(getHeight(), getHeight() + textHeightWithMaxLines - tvBody.getHeight());
            valueAnimatorAlpha = ValueAnimator.ofFloat(animAlphaStart, ALPHA_OPAQUE);
            gradientAnimator = ObjectAnimator.ofFloat(ivGradient, "alpha", ALPHA_OPAQUE, ALPHA_TRANSPARENT);
        }
        gradientAnimator.setDuration(animationDurationMillis);
        valueAnimatorAlpha.addUpdateListener(new AlphaAnimatorUpdateListener(tvBody));
        valueAnimatorHeight.addUpdateListener(new HeightAnimatorUpdateListener(this, marginBetweenTextAndBottom));
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(animationDurationMillis);
        animatorSet.playTogether(valueAnimatorHeight, valueAnimatorAlpha);
        animatorSet.start();
        animatorSet.addListener(new CollapseAnimatorListener(this));
        gradientAnimator.start();
    }

    private void setAnimating(boolean animating) {
        this.isAnimating = animating;
    }
    //endregion

    private void onLayoutAnimationEnd() {
        clearAnimation();
        setAnimating(false);
        if (listener != null) {
            listener.onExpandStateChanged(tvBody, !isCollapsed);
        }
        if (isCollapsed) {
            final ViewGroup.LayoutParams layoutParams = getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            setLayoutParams(layoutParams);
        }
    }

    //region UTILS
    @NonNull
    public String getText() {
        if (tvBody == null || tvBody.getText() == null) {
            return "";
        }
        return tvBody.getText().toString();
    }

    public void setText(@Nullable CharSequence text) {
        if (text != null) {
            tvBody.setText(text);
        } else {
            tvBody.setText(null);
        }
        hasChanged = true;
        setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }
    //endregion

    //region ATTRIBUTE SETTERS
    public void setVisibleLineCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Minimum visible lines is 1");
        }

        visibleLineCount = count;
    }

    public void setAnimationDuration(long animationLengthMillis) {
        if (animationLengthMillis < 0) {
            throw new IllegalArgumentException("Animation duration must be a positive value");
        }

        animationDurationMillis = animationLengthMillis;
    }

    public void setAnimationAlphaStart(@FloatRange(from = 0.0, to = 1.0) float alpha) {
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("alpha should be in range 0.0, 1.0");
        }
        animAlphaStart = alpha;
    }

    public void setBodyTextColor(@ColorInt int color) {
        tvBody.setTextColor(color);
    }

    public void setTextSize(@DimenRes int spSize) {
        tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
    }

    public void shouldShowIcon(boolean shouldShowIcon) {
        showIcon = shouldShowIcon;
    }

    public void setExpandCollapseIcons(@DrawableRes int collapseDrawableRes, @DrawableRes int expandDrawableRes,
                                       @ColorInt int color) {
        expandCollapseIconTint = color;
        expandIcon =
            expandDrawableRes == 0 ? AppCompatResources.getDrawable(getContext(), R.drawable.ctv_icv_arrow_down_24)
                : AppCompatResources.getDrawable(getContext(), expandDrawableRes);
        collapseIcon =
            collapseDrawableRes == 0 ? AppCompatResources.getDrawable(getContext(), R.drawable.ctv_icv_arrow_up_24)
                : AppCompatResources.getDrawable(getContext(), collapseDrawableRes);

        if (expandCollapseIconTint != 0) {
            if (expandIcon != null) {
                Drawable wrappedExpand = DrawableCompat.wrap(expandIcon);
                DrawableCompat.setTint(wrappedExpand, expandCollapseIconTint);
            }
            if (collapseIcon != null) {
                Drawable wrappedExpand = DrawableCompat.wrap(collapseIcon);
                DrawableCompat.setTint(wrappedExpand, expandCollapseIconTint);
            }
        }
    }

    public void setViewLessLabel(String label) {
        viewLessLabel = label;
    }

    public void setViewMoreLabel(String label) {
        viewMoreLabel = label;
    }

    public void setExpandButtonTextColor(@ColorInt int color) {
        tvExpand.setTextColor(color);
    }

    public void setExpandButtonTextSize(@DimenRes int textSizeSP) {
        tvExpand.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSP);
    }

    public void setTransitionGradient(@DrawableRes int transitionGradientId) {
        transitionGradient = AppCompatResources.getDrawable(getContext(), transitionGradientId);
        ivGradient.setImageDrawable(transitionGradient);
    }
    //endregion

    //region CALLBACK DECLARATION
    interface OnExpandStateChangedListener {

        void onExpandStateChanged(TextView textView, boolean isExpanded);
    }
    //endregion

    private static class MarginUpdateRunnable implements Runnable {

        private final WeakReference<CollapsibleTextView> collapsibleTextViewWeakReference;

        public MarginUpdateRunnable(CollapsibleTextView collapsibleTextView) {
            this.collapsibleTextViewWeakReference = new WeakReference<>(collapsibleTextView);
        }

        @Override
        public void run() {
            if (collapsibleTextViewWeakReference.get() != null) {
                updateMainTextViewMargin(collapsibleTextViewWeakReference.get());
            }
        }

        private void updateMainTextViewMargin(CollapsibleTextView ctv) {
            ctv.marginBetweenTextAndBottom = ctv.getHeight() - ctv.tvBody.getHeight();
        }
    }

    private static class AlphaAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        private final WeakReference<TextView> textViewWeakReference;

        public AlphaAnimatorUpdateListener(TextView textViewToAnimate) {
            this.textViewWeakReference = new WeakReference<>(textViewToAnimate);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (textViewWeakReference.get() != null) {
                textViewWeakReference.get().setAlpha((Float) animation.getAnimatedValue());
            }
        }
    }

    private static class CollapseAnimatorListener implements Animator.AnimatorListener {

        private final WeakReference<CollapsibleTextView> animatedViewWeakReference;

        public CollapseAnimatorListener(CollapsibleTextView view) {
            this.animatedViewWeakReference = new WeakReference<>(view);
        }

        @Override
        public void onAnimationStart(Animator animation) {
            //EMPTY
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (animatedViewWeakReference.get() != null) {
                animatedViewWeakReference.get().onLayoutAnimationEnd();
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            //EMPTY
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            //EMPTY
        }
    }

    private static class HeightAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        private final WeakReference<CollapsibleTextView> animatedViewWeakReference;
        private final int marginBetweenTextAndBottom;

        private HeightAnimatorUpdateListener(CollapsibleTextView animatedViewWeakReference,
                                             int marginBetweenTextAndBottom) {
            this.marginBetweenTextAndBottom = marginBetweenTextAndBottom;
            this.animatedViewWeakReference = new WeakReference<>(animatedViewWeakReference);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final CollapsibleTextView collapsibleTextView = animatedViewWeakReference.get();
            if (collapsibleTextView != null) {
                int newHeight = (int) animation.getAnimatedValue();
                final int maxHeight = newHeight - marginBetweenTextAndBottom;
                collapsibleTextView.getLayoutParams().height = newHeight;
                collapsibleTextView.tvBody.setMaxHeight(maxHeight);
                collapsibleTextView.requestLayout();
            }
        }
    }
}
