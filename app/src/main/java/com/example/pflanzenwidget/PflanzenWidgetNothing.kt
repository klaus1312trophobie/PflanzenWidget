package com.example.pflanzenwidget

/** Same data and logic as [PflanzenWidget], dressed in Nothing-style black / white / red. */
class PflanzenWidgetNothing : PflanzenWidget() {
    override val layoutRes = R.layout.widget_layout_nothing
    override val nothing = true
}
