package com.remind.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.remind.widget.ui.AppRoot
import com.remind.widget.ui.theme.RemindTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openGroupId = intent?.getLongExtra(EXTRA_OPEN_GROUP_ID, -1L) ?: -1L
        setContent {
            RemindTheme {
                AppRoot(initialGroupId = openGroupId)
            }
        }
    }

    companion object {
        /** 从通知 / 小组件点进来时，直接落到某个栏目 */
        const val EXTRA_OPEN_GROUP_ID = "open_group_id"
    }
}
