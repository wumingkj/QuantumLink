package com.wuming.quantumlink.ui.navigation

/**
 * 导航页定义
 */
enum class Screen(val title: String) {
    IM("消息"),
    CONTACTS("通讯录"),
    FORUM("论坛"),
    VPN("VPN"),
    SETTINGS("设置");

    companion object {
        /** 底部导航栏显示的页面 */
        val BOTTOM_NAV = listOf(IM, CONTACTS, FORUM, VPN, SETTINGS)
    }
}
