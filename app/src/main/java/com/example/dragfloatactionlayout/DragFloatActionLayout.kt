package com.example.dragfloatactionlayout

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt


class DragFloatActionLayout : LinearLayout {
    private var parentHeight: Int = 0
        get() {
            return field - padding.toInt()
        }
    private var parentWidth: Int = 0
        get() {
            return field - padding.toInt()
        }
    private var padding: Float
    private var isRight: Boolean = true
    private lateinit var contentLayout: LinearLayout
    private lateinit var iconBar: ImageView
    private var mContext: Context
    private val iconBarSize = 46f
    private var valueAnimStartValue = 0
    private var valueAnimEndValue = 0
    private lateinit var thisWidthAnim: ValueAnimator
    private val thisWidthAnimDuration = 300L//毫秒
    private val noOperationTime = 5//秒  无操作时间
    private val dockedTime = 100L//毫秒
    private val stickyTime = 300L//毫秒
    private var isOpen = true
        set(value) {
            field = value
            iconBar.setImageResource(if (field) R.drawable.icon_close else R.drawable.icon_open)
        }
    private var isDocking = false
        set(value) {
            field = value
            iconBar.alpha = if (field) 0.6f else 1f
        }

    private val MAX_CLICK_DISTANCE = 8f

    private var isMove = false
    private var pressedX = 0
    private var pressedY = 0
    private var contentWidth = 0
    private lateinit var mLayoutParams: ConstraintLayout.LayoutParams
    private lateinit var contentLayoutParams: LayoutParams
    private lateinit var timerObservable: Flowable<Long>
    private var disposable: Disposable? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        mContext = context
        padding = dip2px(20f).toFloat()
        orientation = HORIZONTAL
        gravity = Gravity.END
        initView()
        initAnim()

    }

    private fun initView() {
        //初始化icon按钮，和contentLayout布局
        contentLayout = LinearLayout(context)
        iconBar = ImageView(context)
        contentLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        contentLayout.layoutParams = contentLayoutParams
        contentLayout.gravity = Gravity.START
        contentLayout.setBackgroundResource(R.drawable.sp_bg)
        iconBar.layoutParams = LayoutParams(dip2px(iconBarSize), LayoutParams.MATCH_PARENT)
        iconBar.setPadding(dip2px(16f), 0, dip2px(16f), 0)
        iconBar.setImageResource(R.drawable.icon_close)
        iconBar.setBackgroundResource(R.drawable.sp_bg)
    }

    private fun initAnim() {
        //计时器
        timerObservable = RxUtils.timer(noOperationTime).filter {
            //没有操作时间后执行动画
            it == noOperationTime.toLong()
        }.flatMap {

            if (isOpen) {
                return@flatMap Flowable.just(it).doOnNext {
                    foldableAnim()
                }
                    //这里需要执行完折叠动画后才能够执行停靠动画；折叠动画改变该布局的宽度，停靠动画改变的坐标，同时执行冲突
                    .delay(thisWidthAnimDuration, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
            } else {
                return@flatMap Flowable.just(it)
            }

        }.doOnNext {
            //这里必须获取一次，因为parentWidth赋值再DOWN触发时
            parentWidth = (parent as ViewGroup).width
            //执行停靠动画
            val xBy =
                animate().setInterpolator(DecelerateInterpolator()).setDuration(dockedTime)
                    .xBy(
                        if (isRight) parentWidth + padding - width - x else -x
                    )
            xBy.withEndAction {
                //改变停靠状态
                isDocking = true
            }
            xBy.start()

        }
        //初始化折叠动画
        thisWidthAnim = ValueAnimator.ofInt(valueAnimStartValue, valueAnimEndValue)
        thisWidthAnim.duration = thisWidthAnimDuration
        thisWidthAnim.doOnEnd {
            //切换折叠状态
            isOpen = !isOpen

            //如果是展开状态，需要将该布局的宽以及contentLayout的宽设置为内容包裹；当contentLayout子view宽度改变时，contentLayout以及该布局需要同步改变，否则 contentLayout子布局被遮挡
            if (isOpen) {
                mLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                layoutParams = mLayoutParams
                contentLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                contentLayout.layoutParams = contentLayoutParams
            }
        }
        thisWidthAnim.addUpdateListener {
            //改变该布局宽度
            val animatedValue = it.animatedValue as Int
            mLayoutParams.width = animatedValue
            layoutParams = mLayoutParams
        }
        //初始化
        post {
            mLayoutParams = layoutParams as ConstraintLayout.LayoutParams
            //初始化布局后，误操作时间后需要自动折叠布局以及停靠
            disposable?.dispose()
            disposable = timerObservable.subscribe()
        }
        //点击
        iconBar.setOnClickListener {
            //当布局停靠时，再次点击则平移到初始padding位置
            if (isDocking) {
                val xBy =
                    animate().setInterpolator(DecelerateInterpolator()).setDuration(dockedTime).xBy(
                        if (isRight) -padding else padding
                    )
                xBy.withEndAction {
                    //改变停靠状态
                    isDocking = false
                    foldableAnim()
                }
                xBy.start()
            } else {
                foldableAnim()
            }

        }
    }

    //布局折叠动画
    private fun foldableAnim() {
        //需要将contentLayout 宽度固定，否则contentLayout子view会挤压
        contentWidth = contentLayout.width
        contentLayoutParams.width = contentWidth
        contentLayout.layoutParams = contentLayoutParams
        //动画执行状态
        val parentWidthAnimIsRunning = thisWidthAnim.isRunning
        if (!parentWidthAnimIsRunning) {
            //是否打开状态
            if (isOpen) {
                //折叠动画
                valueAnimStartValue = width
                valueAnimEndValue = width - contentWidth
                thisWidthAnim.setIntValues(valueAnimStartValue, valueAnimEndValue)
            } else {
                //展开动画
                valueAnimStartValue = width
                valueAnimEndValue = width + contentWidth
                thisWidthAnim.setIntValues(valueAnimStartValue, valueAnimEndValue)
            }
            thisWidthAnim.start()

        }
    }
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (thisWidthAnim.isRunning)
            return false
        val rawX = ev.rawX.toInt()
        val rawY = ev.rawY.toInt()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                //所有手指按下取消倒计时，抬起时重新计时
                disposable?.dispose()
                pressedX = rawX
                pressedY = rawY
                isMove = false
                if (parent != null && parent is ViewGroup) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    parentWidth = (parent as ViewGroup).width
                    parentHeight = (parent as ViewGroup).height
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = pressedX - rawX
                val dy = pressedY - rawY
                val dv = sqrt((dx * dx + dy * dy).toDouble())
                if (dv > dip2px(MAX_CLICK_DISTANCE))
                    isMove = true
            }

            MotionEvent.ACTION_UP -> {
                isMove = false
                //所有手指抬起开始计时执行折叠以及停靠动画
                disposable = timerObservable.subscribe()
            }
        }
        return isMove
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rawX = event.rawX.toInt()
        val rawY = event.rawY.toInt()
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = pressedX - rawX
                val dy = pressedY - rawY
                var vx = x - dx
                var vy = y - dy
//                LogUtils.debug("onTouchEvent", "dx:$dx  dy:$dy  vx:$vx  vy:$vy")
                if (vx <= 0) {
                    vx = 0f
                }
                val maxX = parentWidth + padding - width
                val maxY = parentHeight + padding - height
                if (vx >= maxX)
                    vx = maxX.toFloat()
                if (vy <= 0)
                    vy = 0f
                if (vy >= maxY)
                    vy = maxY.toFloat()
                x = vx
                y = vy
                pressedX = rawX
                pressedY = rawY
            }

            MotionEvent.ACTION_UP -> {
                isRight = rawX >= parentWidth / 2
                val byX = if (isRight) parentWidth - width - x else padding - x
                animate().setInterpolator(DecelerateInterpolator()).setDuration(stickyTime).xBy(
                    byX
                ).start()
                if (isRight) {
                    mLayoutParams.startToStart = -1
                    mLayoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                } else {
                    mLayoutParams.endToEnd = -1
                    mLayoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                }
                layoutParams = mLayoutParams
                isDocking = false
                //拖拽完成后，执行动画
                disposable = timerObservable.subscribe()
            }
        }
        return true
    }



    override fun onFinishInflate() {
        super.onFinishInflate()
        //这里getChildAt(0)要一直取0，childCount自减
        for (i in 0 until childCount) {
            val childAt = getChildAt(0)
            removeView(childAt)
            contentLayout.addView(childAt)
        }
        addView(contentLayout)
        addView(iconBar)
    }

    fun dip2px(dipValue: Float) =
        (mContext.resources.displayMetrics.density * dipValue + 0.5).toInt()
}
