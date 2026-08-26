package ru.radiationx.anilibria.screen.player

import android.content.Context
import androidx.leanback.widget.Action
import ru.radiationx.anilibria.R
import ru.radiationx.shared.ktx.android.getCompatDrawable

class ResizeAction(context: Context) : Action(R.id.player_action_resize.toLong()) {

    init {
        icon = context.getCompatDrawable(R.drawable.ic_aspect_ratio)
        label1 = "Масштаб изображения"
    }
}
