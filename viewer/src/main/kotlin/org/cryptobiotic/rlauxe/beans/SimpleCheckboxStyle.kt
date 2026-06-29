package org.cryptobiotic.rlauxe.beans

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.AbstractButton
import javax.swing.Icon

// TODO doesnt really work
// https://stackoverflow.com/questions/22691353/scale-a-jcheckbox-box
class SimpleCheckboxStyle(fontsize: Int) : Icon {
    protected var dimension: Int = 16

    init {
        this.dimension = fontsize
    }

    override fun paintIcon(component: Component, g: Graphics, x: Int, y: Int) {
        val buttonModel = (component as AbstractButton).getModel()

        val componentSize = component.getSize()
        val y_offset = (component.getSize().getHeight() / 2).toInt() - (this.dimension / 2)
        val x_offset = 2 // this is wrong

        if (buttonModel.isSelected()) {
            g.setColor(Color(0, 60, 120))
        } else if (buttonModel.isRollover()) {
            g.setColor(Color.BLACK)
        } else {
            g.setColor(Color.DARK_GRAY)
        }
        g.fillRect(x_offset, y_offset, this.dimension, this.dimension)
        if (buttonModel.isPressed()) {
            g.setColor(Color.GRAY)
        } else if (buttonModel.isRollover()) {
            g.setColor(Color(240, 240, 250))
        } else {
            g.setColor(Color.WHITE)
        }
        g.fillRect(1 + x_offset, y_offset + 1, this.dimension - 2, this.dimension - 2)
        if (buttonModel.isSelected()) {
            val r_x = 1
            g.setColor(Color.GRAY)
            g.fillRect(x_offset + r_x + 3, y_offset + 3 + r_x, this.dimension - (7 + r_x), this.dimension - (7 + r_x))
        }
    }

    override fun getIconWidth(): Int {
        return this.dimension
    }

    override fun getIconHeight(): Int {
        return this.dimension
    }
}