package com.arjunaayush.looply.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.arjunaayush.looply.R

/**
 * Looply vector icons. All vector drawables are standard 24x24 vectors.
 */
object LinkerlyIcons {
    val Search: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_search_outlined)
    val Add: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_add_outlined)
    val Close: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_close_outlined)
    val ChevronLeft: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_chevron_left_outlined)
    val ChevronRight: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_chevron_right_outlined)
    val ArrowBack: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_outlined)
    val Bookmark: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_bookmark_outlined)
    val VerticalDots: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_three_vertical_dots)
    val Warning: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_warning_outlined)
    val Info: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_info_outlined)
    val Tune: ImageVector
        @Composable
        get() = Buttons.Tune
    val DragHandle: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_drag_handle)
    val Inbox: ImageVector
        @Composable
        get() = ImageVector.vectorResource(id = R.drawable.ic_inbox_outlined)

    object NavBar {
        val Home: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_home_outlined)
        val HomeSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_home_filled)
        val Search: ImageVector
            @Composable
            get() = LinkerlyIcons.Search
        val SearchSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_search_filled)
        val Folders: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_folder_outlined)
        val FoldersSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_folder_filled)
        val Profile: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_person_outlined)
        val ProfileSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_person_filled)
    }

    object Buttons {
        val Undo: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_undo_outlined)
        val Redo: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_redo_outlined)
        val Bookmark: ImageVector
            @Composable
            get() = LinkerlyIcons.Bookmark
        val PlayVideo: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_play_circle_outlined)
        val Edit: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_edit_outlined)
        val Delete: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_delete_outlined)
        val Up: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_arrow_up_outlined)
        val Down: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_arrow_down_outlined)
        val Share: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_share_outlined)
        val Copy: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_copy_outlined)
        val Paste: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_paste_outlined)
        val Restore: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_restore_outlined)
        val Calendar: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_calendar_outlined)
        val Tune: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_tune_outlined)
        val OpenInBrowser: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_open_outlined)
        val Pin: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_pin_outlined)
        val PinSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_pin_filled)
        val CheckCircle: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_check_circle_outlined)
        val CheckCircleFilled: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_check_circle_filled)
        val Favorite: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_favorite_outlined)
        val FavoriteSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_favorite_filled)
        val Sort: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_sort_outlined)
        val Filter: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_filter_outlined)
        val History: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_history_outlined)
        val Book: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_book_outlined)
        val AddFolder: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_folder_add_outlined)
        val Fire: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_fire_outlined)
        val Star: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_star_outlined)
        val StarSelected: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_star_filled)
        val Import: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_import_outlined)
        val Refresh: ImageVector
            @Composable
            get() = Restore
    }

    object FolderOverlays {
        val Web: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_language_outlined)
        val Code: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_code_outlined)
        val Phone: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_phone_outlined)
        val Cloud: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_cloud_outlined)
        val Bolt: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_bolt_outlined)
        val Database: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_database_outlined)
        val Pen: ImageVector
            @Composable
            get() = Buttons.Edit
        val Fire: ImageVector
            @Composable
            get() = Buttons.Fire
        val Star: ImageVector
            @Composable
            get() = Buttons.Star
        val Briefcase: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_briefcase_outlined)
        val Music: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_music_outlined)
        val Cart: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_cart_outlined)
        val Lightbulb: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_lightbulb_outlined)
        val Heart: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_heart_outlined)
        val Wallet: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_wallet_outlined)
        val Plane: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_plane_outlined)
        val Terminal: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_terminal_outlined)
        val Shield: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_shield_outlined)
        val Coffee: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_coffee_outlined)
        val Gamepad: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_gamepad_outlined)
        val Camera: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_camera_outlined)
        val Fitness: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_dumbbell_outlined)
        val Archive: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_archive_outlined)
        val Compass: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_compass_outlined)
        val Book: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_book_outlined)
        val Art: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_palette_outlined)
        val Bookmark: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_bookmark_outlined)
        val Lock: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_lock_outlined)
        val Tag: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_tag_outlined)
        val Video: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_play_circle_outlined)
        val Burger: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_burger_outlined)
        val Apple: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_apple_outlined)
        val Laptop: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_laptop_outlined)
        val Headphones: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_headphones_outlined)
        val School: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_school_outlined)
        val Gift: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_gift_outlined)
        val Rocket: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_rocket_outlined)
        val Film: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_film_outlined)
        val Money: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_money_outlined)
    }

    object Profile {
        val Favorites: ImageVector
            @Composable
            get() = Buttons.Favorite
        val History: ImageVector
            @Composable
            get() = Buttons.History
        val Trash: ImageVector
            @Composable
            get() = Buttons.Delete
        val Import: ImageVector
            @Composable
            get() = Buttons.Import
        val Export: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_export_outlined)
        val Settings: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_settings_outlined)
        val Help: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_help_outlined)
        val Lock: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_lock_outlined)
        val About: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_info_outlined)
        val Account: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_account_circle_outlined)
        val PremiumStar: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_premium_filled)
        val ListView: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_list_outlined)
        val GridView: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_grid_outlined)
    }

    object Settings {
        val Appearance: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_palette_outlined)
        val AppIcon: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_app_icon_outlined)
        val Language: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_language_outlined)
        val Notification: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_notification_outlined)
        val QuickSave: ImageVector
            @Composable
            get() = FolderOverlays.Bolt
        val Tag: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_tag_outlined)
        val Shortcuts: ImageVector
            @Composable
            get() = ImageVector.vectorResource(id = R.drawable.ic_widget_outlined)
        val Folder: ImageVector
            @Composable
            get() = NavBar.Folders
    }
}
