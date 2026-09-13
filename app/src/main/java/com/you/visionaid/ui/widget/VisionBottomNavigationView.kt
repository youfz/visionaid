package com.you.visionaid.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.navOptions
import com.you.visionaid.R

/**
 * Bottom navigation tailored to VisionAid's two top-level destinations.
 *
 * Labels, typeface, item margins, tint, icon size and icon/label spacing can be
 * supplied from XML. The corresponding setters allow the same values to be
 * changed at runtime without rebuilding the view.
 */
class VisionBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private data class NavigationItem(
        val container: LinearLayout,
        val icon: ImageView,
        val label: AppCompatTextView,
    )

    private val items = linkedMapOf<Int, NavigationItem>()
    private var selectedItemId = View.NO_ID
    private var itemSelectedListener: ((Int) -> Unit)? = null
    private var horizontalItemMargin = 0
    private var verticalItemMargin = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        val defaults = context.resources
        val values = context.obtainStyledAttributes(
            attrs,
            R.styleable.VisionBottomNavigationView,
            defStyleAttr,
            0,
        )
        val enhanceText = values.getText(
            R.styleable.VisionBottomNavigationView_navigationEnhanceText,
        ) ?: context.getText(R.string.enhance)
        val settingsText = values.getText(
            R.styleable.VisionBottomNavigationView_navigationSettingsText,
        ) ?: context.getText(R.string.settings)
        val textSize = values.getDimension(
            R.styleable.VisionBottomNavigationView_navigationTextSize,
            defaults.getDimension(R.dimen.bottom_navigation_text_size),
        )
        val tint = values.getColorStateList(
            R.styleable.VisionBottomNavigationView_navigationItemTint,
        ) ?: ResourcesCompat.getColorStateList(
            resources,
            R.color.bottom_navigation_item,
            context.theme,
        ) ?: ColorStateList.valueOf(ResourcesCompat.getColor(resources, R.color.white, context.theme))
        horizontalItemMargin = values.getDimensionPixelSize(
            R.styleable.VisionBottomNavigationView_navigationItemHorizontalMargin,
            defaults.getDimensionPixelSize(R.dimen.bottom_navigation_item_horizontal_margin),
        )
        verticalItemMargin = values.getDimensionPixelSize(
            R.styleable.VisionBottomNavigationView_navigationItemVerticalMargin,
            defaults.getDimensionPixelSize(R.dimen.bottom_navigation_item_vertical_margin),
        )
        val iconTextSpacing = values.getDimensionPixelSize(
            R.styleable.VisionBottomNavigationView_navigationIconTextSpacing,
            defaults.getDimensionPixelSize(R.dimen.bottom_navigation_icon_text_spacing),
        )
        val iconSize = values.getDimensionPixelSize(
            R.styleable.VisionBottomNavigationView_navigationIconSize,
            defaults.getDimensionPixelSize(R.dimen.bottom_navigation_icon_size),
        )
        val fontResource = values.getResourceId(
            R.styleable.VisionBottomNavigationView_navigationFontFamily,
            0,
        )
        val fontName = values.getString(
            R.styleable.VisionBottomNavigationView_navigationFontFamily,
        )
        values.recycle()

        addNavigationItem(
            destinationId = R.id.cameraFragment,
            iconResource = R.drawable.ic_enhance,
            text = enhanceText,
            textSize = textSize,
            tint = tint,
            iconSize = iconSize,
            iconTextSpacing = iconTextSpacing,
        )
        addNavigationItem(
            destinationId = R.id.settingsFragment,
            iconResource = R.drawable.ic_settings,
            text = settingsText,
            textSize = textSize,
            tint = tint,
            iconSize = iconSize,
            iconTextSpacing = iconTextSpacing,
        )

        val typeface = when {
            fontResource != 0 -> runCatching {
                ResourcesCompat.getFont(context, fontResource)
            }.getOrNull()
            !fontName.isNullOrBlank() -> Typeface.create(fontName, Typeface.NORMAL)
            else -> null
        }
        typeface?.let(::setItemTypeface)
    }

    fun setOnItemSelectedListener(listener: ((Int) -> Unit)?) {
        itemSelectedListener = listener
    }

    fun setSelectedItem(@IdRes destinationId: Int) {
        if (destinationId == selectedItemId || destinationId !in items) return
        selectedItemId = destinationId
        items.forEach { (id, item) ->
            val selected = id == destinationId
            item.container.isSelected = selected
            item.icon.isSelected = selected
            item.label.isSelected = selected
        }
    }

    fun setItemText(@IdRes destinationId: Int, text: CharSequence) {
        items[destinationId]?.let { item ->
            item.label.text = text
            item.container.contentDescription = text
        }
    }

    fun getItemText(@IdRes destinationId: Int): CharSequence? =
        items[destinationId]?.label?.text

    fun setItemTypeface(typeface: Typeface?) {
        items.values.forEach { it.label.typeface = typeface }
    }

    fun setItemTypeface(@IdRes destinationId: Int, typeface: Typeface?) {
        items[destinationId]?.label?.typeface = typeface
    }

    fun setItemTextSize(unit: Int, size: Float) {
        items.values.forEach { it.label.setTextSize(unit, size) }
    }

    fun setItemTextSize(@IdRes destinationId: Int, unit: Int, size: Float) {
        items[destinationId]?.label?.setTextSize(unit, size)
    }

    fun setItemTint(tint: ColorStateList) {
        items.values.forEach { item ->
            item.label.setTextColor(tint)
            ImageViewCompat.setImageTintList(item.icon, tint)
        }
    }

    /** Applies the same outer margin to every navigation item. Values are pixels. */
    fun setItemMargins(horizontal: Int, vertical: Int) {
        horizontalItemMargin = horizontal
        verticalItemMargin = vertical
        items.keys.forEach(::applyItemMargins)
    }

    /** Applies individual outer margins to one navigation item. Values are pixels. */
    fun setItemMargins(
        @IdRes destinationId: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val item = items[destinationId] ?: return
        (item.container.layoutParams as LayoutParams).apply {
            setMargins(left, top, right, bottom)
            item.container.layoutParams = this
        }
    }

    /** Keeps clicks and selection state synchronized with Navigation Component. */
    fun setupWithNavController(navController: NavController) {
        setOnItemSelectedListener { destinationId ->
            if (navController.currentDestination?.id == destinationId) return@setOnItemSelectedListener
            if (navController.popBackStack(destinationId, false)) {
                return@setOnItemSelectedListener
            }
            navController.navigate(
                destinationId,
                null,
                navOptions {
                    launchSingleTop = true
                },
            )
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            destination.hierarchy.firstOrNull { it.id in items }?.id?.let(::setSelectedItem)
        }
        navController.currentDestination?.hierarchy
            ?.firstOrNull { it.id in items }
            ?.id
            ?.let(::setSelectedItem)
    }

    private fun addNavigationItem(
        @IdRes destinationId: Int,
        iconResource: Int,
        text: CharSequence,
        textSize: Float,
        tint: ColorStateList,
        iconSize: Int,
        iconTextSpacing: Int,
    ) {
        val container = LinearLayout(context).apply {
            id = destinationId
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
            contentDescription = text
            setOnClickListener {
                setSelectedItem(destinationId)
                itemSelectedListener?.invoke(destinationId)
            }
            ViewCompat.setAccessibilityDelegate(
                this,
                object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = android.widget.Button::class.java.name
                        info.isSelected = host.isSelected
                    }
                },
            )
        }
        val icon = AppCompatImageView(context).apply {
            setImageResource(iconResource)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isDuplicateParentStateEnabled = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            ImageViewCompat.setImageTintList(this, tint)
        }
        container.addView(icon, LayoutParams(iconSize, iconSize))

        val label = AppCompatTextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            includeFontPadding = false
            isDuplicateParentStateEnabled = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setTextColor(tint)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        }
        container.addView(
            label,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = iconTextSpacing
            },
        )

        addView(
            container,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        items[destinationId] = NavigationItem(container, icon, label)
        applyItemMargins(destinationId)
    }

    private fun applyItemMargins(@IdRes destinationId: Int) {
        val item = items[destinationId] ?: return
        (item.container.layoutParams as LayoutParams).apply {
            setMargins(
                horizontalItemMargin,
                verticalItemMargin,
                horizontalItemMargin,
                verticalItemMargin,
            )
            item.container.layoutParams = this
        }
    }
}
