package com.example.likeview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.PathEffect;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;

/**
 * @Author Xiao
 * @Date 2019/5/9
 * @Description
 */
public class LikeView extends View {
    private static final String TAG = "LikeView";
    private static final float SHINING_ANGLE = 120;
    private static final float LIKE_BIG_SCALE = 1.2f;
    private static final float LIKE_SMALL_SCALE = 0.8f;

    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable likeDrawable;
    private Drawable unLikeDrawable;
    private boolean useShining;
    private float shiningOffsetX;
    private float shiningOffsetY;
    private float shiningPointHeight;
    private float shiningPointWidth;
    private float shiningExtraAngle;
    private int shiningColor;
    private int numTextColor;
    private float numTextSize;
    private float numPadding;
    private long likeDuration;

    private boolean liked;
    private float likeScale = 1;
    private float likeHeight;
    private float likeWidth;
    private RectF shiningPointRectF = new RectF();
    private Path shiningPointPath = new Path();
    private Path shiningArcPath = new Path();
    private RectF shiningRectF = new RectF();
    private PathMeasure mPathMeasure = new PathMeasure();
    private float shiningRadius;
    private float shiningFraction;
    private float numFraction;
    private int currentNum;
    private float numWidth;
    private String leftNum;
    private String rightNum1;
    private String rightNum2;
    private float offsetNum1;
    private float offsetNum2;


    private ObjectAnimator shiningAnimator;
    private ObjectAnimator numAnimator;
    private AnimatorSet mAnimatorSet;


    public LikeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        //关闭硬件加速
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LikeView);
        liked = typedArray.getBoolean(R.styleable.LikeView_liked, false);
        likeDrawable = typedArray.getDrawable(R.styleable.LikeView_likeDrawable);
        unLikeDrawable = typedArray.getDrawable(R.styleable.LikeView_unLikeDrawable);
        numPadding = typedArray.getDimension(R.styleable.LikeView_numPadding, dp2px(2));
        numTextSize = typedArray.getDimension(R.styleable.LikeView_numTextSize, dp2px(14));
        numTextColor = typedArray.getColor(R.styleable.LikeView_numTextColor, Color.GRAY);
        likeDuration = typedArray.getInteger(R.styleable.LikeView_likeDuration, 300);
        currentNum = typedArray.getInteger(R.styleable.LikeView_currentNum, 1);

        useShining = typedArray.getBoolean(R.styleable.LikeView_useShining, true);
        if (useShining) {
            shiningColor = typedArray.getColor(R.styleable.LikeView_shiningColor, Color.GRAY);
            shiningOffsetX = typedArray.getDimension(R.styleable.LikeView_shiningOffsetX, 0);
            shiningOffsetY = typedArray.getDimension(R.styleable.LikeView_shiningOffsetY, 0);
            shiningPointWidth = typedArray.getDimension(R.styleable.LikeView_shiningPointWidth, dp2px(2f));
            shiningPointHeight = typedArray.getDimension(R.styleable.LikeView_shiningPointHeight, dp2px(4.5f));
            shiningExtraAngle = typedArray.getFloat(R.styleable.LikeView_shiningExtraAngle, 0);
        }
        typedArray.recycle();

        initParams();
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!getAnimatorSet().isRunning()) {
                    liked = !liked;
                    getAnimatorSet().start();
                }
            }
        });
    }


    /**
     * 设置参数
     */
    private void initParams() {
        //设置闪光绘制尺寸
        if (likeDrawable != null && unLikeDrawable != null) {
            likeWidth = likeDrawable.getIntrinsicWidth();
            likeHeight = likeDrawable.getIntrinsicHeight();
            shiningRadius = useShining ? likeWidth / 2f : 0;
        }

        //闪光点圆角矩形高度上限是shiningRadius
        shiningPointHeight = Math.min(shiningPointHeight, shiningRadius);

        //限制闪光偏移范围
        //shiningOffsetX的取值区间[- likeWidth * (LIKE_BIG_SCALE - 1), likeWidth * (LIKE_BIG_SCALE - 1)]
        shiningOffsetX = Math.min(shiningOffsetX, likeWidth * (LIKE_BIG_SCALE - 1));
        shiningOffsetX = Math.max(shiningOffsetX, - likeWidth * (LIKE_BIG_SCALE - 1));
        //shiningOffsetY的取值区间[0, shiningRadius]
        shiningOffsetY += (shiningRadius - shiningPointHeight);
        shiningOffsetY = Math.min(shiningOffsetY, shiningRadius);
        shiningOffsetY = Math.max(shiningOffsetY, 0);

        shiningExtraAngle = Math.max(shiningExtraAngle, - (180 - SHINING_ANGLE) / 2);
        shiningExtraAngle = Math.min(shiningExtraAngle, (180 - SHINING_ANGLE) / 2);

        //限制numTextSize最大值
        numTextSize = Math.min(numTextSize, likeHeight * LIKE_BIG_SCALE + shiningRadius);
        initNum();
    }


    /**
     * 初始化绘制的数字
     */
    private void initNum() {
        String tempNum1;
        String tempNum2;

        if (!liked) {
            tempNum1 = String.valueOf(currentNum);
            tempNum2 = String.valueOf(currentNum + 1);
            //初始化闪光显示状态、rightNum1和rightNum2的绘制位置
            shiningFraction = 0;
            numFraction = 0;
        } else {
            tempNum1 = String.valueOf(currentNum - 1);
            tempNum2 = String.valueOf(currentNum);
            shiningFraction = 1;
            numFraction = 1;
        }

        //计算滑动位数
        offsetNum1 = 0;
        offsetNum2 = 0;
        int numSlideCount = 0;
        char[] num1Array = tempNum1.toCharArray();
        char[] num2Array = tempNum2.toCharArray();

        if (num1Array.length == num2Array.length) {
            for (int i = 0; i < num1Array.length; i++) {
                if (num1Array[i] != num2Array[i]) {
                    numSlideCount++;
                }
            }
        }


        //点赞数的宽度是tempNum1和tempNum2之间长度更长的那个
        mPaint.setTextSize(numTextSize);
        String maxWidthNum = tempNum1.length() > tempNum2.length() ? tempNum1 : tempNum2;
        numWidth = mPaint.measureText(String.valueOf(maxWidthNum));

        //计算leftNum、rightNum1、rightNum2
        if (numSlideCount != 0) {
            leftNum = tempNum1.substring(0, tempNum1.length() - numSlideCount);
            //当numSlideCount = 0, rightNum1和rightNum2为空，不进行绘制
            rightNum1 = tempNum1.substring(tempNum1.length() - numSlideCount, tempNum1.length());
            rightNum2 = tempNum2.substring(tempNum2.length() - numSlideCount, tempNum2.length());
        } else {
            leftNum = "";
            rightNum1 = tempNum1;
            rightNum2 = tempNum2;
            //整体滑动的时候，上下两个数字需要对齐
            //设置完字体大小后，再计算偏移值
            if (rightNum1.length() > rightNum2.length()) {
                int fillLength = rightNum1.length() - rightNum2.length();
                offsetNum2 = mPaint.measureText(rightNum1.substring(0, fillLength));
            } else {
                int fillLength = rightNum2.length() - rightNum1.length();
                offsetNum1 = mPaint.measureText(rightNum2.substring(0, fillLength));
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //LIKE_BIG_SCALE为放大比例常量，值等于1.2f
        //likeWidth * LIKE_BIG_SCALE是点赞图放大后的宽度
        //numPadding为间隔宽度，默认值是3dp的像素
        int width = (int) (likeWidth * LIKE_BIG_SCALE + numPadding + numWidth);

        //likeHeight * LIKE_BIG_SCALE是点赞图放大后的高度
        int height = (int) (likeHeight * LIKE_BIG_SCALE + shiningRadius - shiningOffsetY);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        canvas.drawColor(Color.parseColor("#9DBB84"));
        //计算点赞图横坐标偏移值，即大图宽度减去原图宽度，再除以2
        float likeLeft = likeWidth * (LIKE_BIG_SCALE - 1) / 2;
        //计算点赞图左上角的纵坐标
        float bigLikeTop = shiningRadius - shiningOffsetY;
        float likeTop = (getHeight() + bigLikeTop) / 2 - likeHeight / 2;
        drawShining(canvas, likeLeft);
        drawLike(canvas, likeLeft, likeTop);

        float numLeft = likeWidth * LIKE_BIG_SCALE + numPadding;
        float numTop = (getHeight() + shiningRadius - shiningOffsetY) / 2;
        drawNum(canvas, numLeft, numTop);

    }

    /**
     * 绘制点赞图标
     */
    private void drawLike(Canvas canvas, float likeLeft, float likeTop) {
        if (likeDrawable != null && unLikeDrawable != null) {
            //根据点赞状态绘制对应的图片
            Drawable drawable = liked ? likeDrawable : unLikeDrawable;
            canvas.save();
            canvas.translate(likeLeft, likeTop);
            //以图片为中心，缩放图片
            canvas.scale(likeScale, likeScale, likeWidth / 2f, likeHeight/ 2f);
            drawable.setBounds(0, 0, (int) likeWidth, (int) likeHeight);
            drawable.draw(canvas);
            canvas.restore();
        }
    }


    /**
     * 绘制数字
     */
    private void drawNum(Canvas canvas, float numLeft, float numTop) {
        mPaint.reset();
        mPaint.setColor(numTextColor);
        mPaint.setTextSize(numTextSize);
        Paint.FontMetrics mFontMetrics = mPaint.getFontMetrics();

        //offsetX是对rightNum添加的额外偏移
        float offsetX = mPaint.measureText(leftNum);
        //由于绘制文字时，绘制出来的文字是在绘制起点的右上角，所以还需要加一个纵向偏移，使文字相对于大图区域垂直居中
        float offsetY =  - (mFontMetrics.descent + mFontMetrics.ascent) / 2;

        //numLeft和numTop是点赞数的横纵坐标基础偏移值，计算的方式和计算点赞图左上角坐标的过程差不多
        //float numLeft = likeWidth * LIKE_BIG_SCALE + numPadding;
        //float numTop = (getHeight() + shiningRadius - shiningOffsetY)/ 2;
        if (!TextUtils.isEmpty(leftNum)) {
            canvas.save();
            canvas.translate(numLeft, numTop + offsetY);
            canvas.drawText(leftNum, 0, 0, mPaint);
            canvas.restore();
        }

        ////numFraction为动画过程中取值的比例
        canvas.save();
        mPaint.setAlpha((int) (255 * (1 - numFraction)));
        canvas.translate(numLeft + offsetX + offsetNum1, numTop * (1 - numFraction) + offsetY);
        canvas.drawText(rightNum1, 0, 0, mPaint);
        canvas.restore();

        canvas.save();
        mPaint.setAlpha((int) (255 * numFraction));
        canvas.translate(numLeft + offsetX +  + offsetNum2, numTop * (2 - numFraction) + offsetY);
        canvas.drawText(rightNum2, 0, 0, mPaint);
        canvas.restore();
    }

    /**
     * 绘制闪光
     */
    private void drawShining(Canvas canvas, float likeLeft) {
        if (!useShining) {
            return;
        }
        mPaint.reset();
        mPaint.setColor(shiningColor);
        shiningPointPath.reset();
        shiningArcPath.reset();

        //闪光点形状是圆角矩形
        //shiningPointWidth是矩形宽度，shiningPointHeight是矩形高度
        shiningPointRectF.set(0, 0, shiningPointWidth, shiningPointHeight);
        shiningPointPath.addRoundRect(shiningPointRectF, dp2px(10), dp2px(10), Path.Direction.CW);

        //闪光的绘制路径是一段弧线
        //计算弧线对应矩形的中心点
        //中心点的横坐标 = 点赞图一半宽度 + 点赞图横向偏移值 + 闪光横向偏移值
        //中心点的纵坐标 = 点赞图一半宽度
        //纵坐标不需要考虑偏移值的原因是，在测量过程中View的高度已经减去了纵向偏移值，闪光的顶部相对于坐标系，永远是x轴
        float shiningCenterX = shiningRadius + likeLeft + shiningOffsetX;
        float shiningCenterY = shiningRadius;

        //shiningFraction为动画过程中取值的比例

        float arcFraction = shiningRadius * shiningFraction;
        shiningRectF.set(shiningCenterX - arcFraction, shiningCenterY - arcFraction, shiningCenterX + arcFraction, shiningCenterY + arcFraction);
        //SHINING_ANGLE为弧线扫过的角度常量，值是120
        //shiningExtraAngle是起始角度改变量
        shiningArcPath.addArc(shiningRectF, -90 - SHINING_ANGLE / 2 + shiningExtraAngle, SHINING_ANGLE);

        mPathMeasure.setPath(shiningArcPath, false);
        //PathMeasure.getLength()是弧线的周长
        //advance为闪光点在弧线上的间隔
        //由于绘制的时候，间隔不是从上一个闪光点的终点到下一个闪光点的起点，而是起点到起点
        //所以 间隔 = (弧线长 - 闪光点宽度) / (闪光点个数 - 1)
        float advance = (mPathMeasure.getLength() - shiningPointWidth) / 3;
        //圆角矩形代替弧线的轮廓
        PathEffect shiningPathEffect = new PathDashPathEffect(shiningPointPath, advance, 0, PathDashPathEffect.Style.ROTATE);
        mPaint.setPathEffect(shiningPathEffect);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAlpha((int) (255 * shiningFraction));
        canvas.drawPath(shiningArcPath, mPaint);
        mPaint.setPathEffect(null);
    }

    private float getShiningFraction() {
        return shiningFraction;
    }

    private void setShiningFraction(float shiningFraction) {
        this.shiningFraction = shiningFraction;
        invalidate();
    }

    private float getLikeScale() {
        return likeScale;
    }

    private void setLikeScale(float likeScale) {
        this.likeScale = likeScale;
        invalidate();
    }

    private float getNumFraction() {
        return numFraction;
    }

    private void setNumFraction(float numFraction) {
        this.numFraction = numFraction;
        invalidate();
    }

    public AnimatorSet getAnimatorSet() {
        if (mAnimatorSet == null) {
            //LIKE_SMALL_SCALE是缩小比例常量，值是0.8f
            //LIKE_BIG_SCALE是放大比例常量，值是1.2f
            //实现的效果是先缩小，然后放大，最后变为正常大小
            ObjectAnimator likeScaleAnimator = ObjectAnimator.ofFloat(this, "likeScale", 1, LIKE_SMALL_SCALE, LIKE_BIG_SCALE, 1);
            likeScaleAnimator.setDuration(likeDuration);

            shiningAnimator = ObjectAnimator.ofFloat(this, "shiningFraction", 0);
            shiningAnimator.setDuration(likeDuration / 2);

            numAnimator = ObjectAnimator.ofFloat(this, "numFraction", 0);
            numAnimator.setDuration(likeDuration);

            mAnimatorSet = new AnimatorSet();
            mAnimatorSet.playTogether(likeScaleAnimator, shiningAnimator, numAnimator);
            mAnimatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    String newNum = liked ? (leftNum + rightNum2) : (leftNum + rightNum1);
                    currentNum = Integer.parseInt(newNum);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }

        if (shiningAnimator != null && numAnimator != null) {
            if (liked) {
                shiningAnimator.setFloatValues(0.5f, 1);
                numAnimator.setFloatValues(0, 1);
            } else {
                //取消点赞的时候，闪光直接消失
                shiningAnimator.setFloatValues(0, 0);
                numAnimator.setFloatValues(1, 0);
            }
        }
        return mAnimatorSet;
    }

    private float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }

    private void resolveParams() {
        float oldWidth = numWidth;
        initNum();
        if (oldWidth != numWidth) {
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getAnimatorSet().end();
    }

    public void setCurrentNum(int currentNum) {
        this.currentNum = currentNum;
        resolveParams();
    }


    public void setLiked(boolean liked) {
        this.liked = liked;
        resolveParams();
    }

    public boolean isLiked() {
        return liked;
    }






}
