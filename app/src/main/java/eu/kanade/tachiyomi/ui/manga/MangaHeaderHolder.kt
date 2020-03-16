package eu.kanade.tachiyomi.ui.manga

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersAdapter
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import java.util.Locale
import kotlinx.android.synthetic.main.manga_header_item.*

class MangaHeaderHolder(
    private val view: View,
    private val adapter: ChaptersAdapter,
    startExpanded: Boolean
) : BaseFlexibleViewHolder(view, adapter) {

    init {
        start_reading_button.setOnClickListener { adapter.coverListener.readNextChapter() }
        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = adapter.coverListener.topCoverHeight()
        }
        more_button.setOnClickListener { expandDesc() }
        manga_summary.setOnClickListener { expandDesc() }
        less_button.setOnClickListener {
            manga_summary.maxLines = 3
            manga_genres_tags.gone()
            less_button.gone()
            more_button_group.visible()
        }
        manga_genres_tags.setOnTagClickListener {
            adapter.coverListener.tagClicked(it)
        }
        filter_button.setOnClickListener { adapter.coverListener.showChapterFilter() }
        filters_text.setOnClickListener { adapter.coverListener.showChapterFilter() }
        chapters_title.setOnClickListener { adapter.coverListener.showChapterFilter() }
        webview_button.setOnClickListener { adapter.coverListener.openInWebView() }
        share_button.setOnClickListener { adapter.coverListener.prepareToShareManga() }
        favorite_button.setOnClickListener {
            adapter.coverListener.favoriteManga(false)
        }
        favorite_button.setOnLongClickListener {
            adapter.coverListener.favoriteManga(true)
            true
        }
        manga_full_title.setOnLongClickListener {
            adapter.coverListener.copyToClipboard(manga_full_title.text.toString(), R.string.manga_info_full_title_label)
            true
        }
        manga_author.setOnLongClickListener {
            adapter.coverListener.copyToClipboard(manga_author.text.toString(), R.string.manga_info_author_label)
            true
        }
        manga_cover.setOnClickListener { adapter.coverListener.zoomImageFromThumb(cover_card) }
        track_button.setOnClickListener { adapter.coverListener.showTrackingSheet() }
        if (startExpanded)
            expandDesc()
    }

    private fun expandDesc() {
        if (more_button.visibility == View.VISIBLE) {
            manga_summary.maxLines = Integer.MAX_VALUE
            manga_genres_tags.visible()
            less_button.visible()
            more_button_group.gone()
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(item: MangaHeaderItem, manga: Manga) {
        val presenter = adapter.coverListener.mangaPresenter()
        manga_full_title.text = manga.currentTitle()

        if (manga.currentGenres().isNullOrBlank().not())
            manga_genres_tags.setTags(manga.currentGenres()?.split(", ")?.map(String::trim))
        else
            manga_genres_tags.setTags(emptyList())

        if (manga.currentAuthor() == manga.currentArtist() ||
            manga.currentArtist().isNullOrBlank())
            manga_author.text = manga.currentAuthor()?.trim()
        else {
            manga_author.text = "${manga.currentAuthor()?.trim()}, ${manga.currentArtist()}"
        }
        manga_summary.text = manga.currentDesc()?.trim() ?: itemView.context.getString(R.string
            .no_description)

        manga_summary.post {
            if ((manga_summary.lineCount < 3 && manga.currentGenres().isNullOrBlank()) ||
                less_button.visibility == View.VISIBLE) {
                more_button_group.gone()
            } else
                more_button_group.visible()
        }
        manga_summary_label.text = itemView.context.getString(R.string.about_this,
            itemView.context.getString(
                when {
                    manga.mangaType() == Manga.TYPE_MANHWA -> R.string.manhwa
                    manga.mangaType() == Manga.TYPE_MANHUA -> R.string.manhua
                    manga.mangaType() == Manga.TYPE_COMIC -> R.string.comic
                    manga.mangaType() == Manga.TYPE_WEBTOON -> R.string.webtoon
                    else -> R.string.manga
                }
            ).toLowerCase(Locale.getDefault()))
        with(favorite_button) {
            icon = ContextCompat.getDrawable(
                itemView.context, when {
                    item.isLocked -> R.drawable.ic_lock_white_24dp
                    manga.favorite -> R.drawable.ic_bookmark_white_24dp
                    else -> R.drawable.ic_add_to_library_24dp
                }
            )
            text = itemView.resources.getString(
                when {
                    item.isLocked -> R.string.unlock
                    manga.favorite -> R.string.in_library
                    else -> R.string.add_to_library
                }
            )
            checked(!item.isLocked && manga.favorite)
        }
        true_backdrop.setBackgroundColor(adapter.coverListener.coverColor()
        ?: itemView.context.getResourceColor(android.R.attr.colorBackground))

        val tracked = presenter.isTracked() && !item.isLocked

        with(track_button) {
            visibleIf(presenter.hasTrackers())
            text = itemView.context.getString(if (tracked) R.string.action_filter_tracked
            else R.string.tracking)

            icon = ContextCompat.getDrawable(itemView.context, if (tracked) R.drawable
                .ic_check_white_24dp else R.drawable.ic_sync_black_24dp)
            checked(tracked)
        }

        with(start_reading_button) {
            val nextChapter = presenter.getNextUnreadChapter()
            visibleIf(presenter.chapters.isNotEmpty() && !item.isLocked)
            isEnabled = (nextChapter != null)
            if (nextChapter != null) {
                val number = adapter.decimalFormat.format(nextChapter.chapter_number.toDouble())
                text = if (nextChapter.chapter_number > 0) resources.getString(
                    if (nextChapter.last_page_read > 0) R.string.continue_reading_chapter
                    else R.string.start_reading_chapter, number
                )
                else {
                    val name = nextChapter.name
                    resources.getString(
                        if (nextChapter.last_page_read > 0) R.string.continue_reading
                        else R.string.start_reading
                    )
                }
            } else {
                text = resources.getString(R.string.start_reading)
            }
        }

        val count = presenter.chapters.size
        chapters_title.text = itemView.resources.getQuantityString(R.plurals.chapters, count, count)

        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = adapter.coverListener.topCoverHeight()
        }

        manga_status.text = (itemView.context.getString(when (manga.status) {
            SManga.ONGOING -> R.string.ongoing
            SManga.COMPLETED -> R.string.completed
            SManga.LICENSED -> R.string.licensed
            else -> R.string.unknown_status
        }))
        manga_source.text = presenter.source.toString()

        filters_text.text = presenter.currentFilters()

        if (!manga.initialized) return
        GlideApp.with(view.context).load(manga)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .signature(ObjectKey(MangaImpl.getLastCoverFetch(manga.id!!).toString()))
            .into(manga_cover)
        GlideApp.with(view.context).load(manga)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .signature(ObjectKey(MangaImpl.getLastCoverFetch(manga.id!!).toString()))
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(backdrop)
    }

    private fun MaterialButton.checked(checked: Boolean) {
        if (checked) {
            backgroundTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(
                    context.getResourceColor(R.attr.colorAccent), 75
                )
            )
            strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
        } else {
            strokeColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(
                    itemView.context.getResourceColor(
                        R.attr.colorOnSurface
                    ), 31
                )
            )
            backgroundTintList =
                ContextCompat.getColorStateList(context, android.R.color.transparent)
        }
    }

    fun setTopHeight(newHeight: Int) {
        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = newHeight
        }
    }

    fun setBackDrop(color: Int) {
        true_backdrop.setBackgroundColor(color)
    }

    fun updateTracking() {
        val presenter = adapter.coverListener.mangaPresenter() ?: return
        val tracked = presenter.isTracked()
        with(track_button) {
            text = itemView.context.getString(if (tracked) R.string.action_filter_tracked
            else R.string.tracking)

            icon = ContextCompat.getDrawable(itemView.context, if (tracked) R.drawable
                .ic_check_white_24dp else R.drawable.ic_sync_black_24dp)
            checked(tracked)
        }
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
