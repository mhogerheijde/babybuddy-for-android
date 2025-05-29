package eu.pkgsoftware.babybuddywidgets.history

import android.graphics.Color
import android.graphics.Color.TRANSPARENT
import android.view.MotionEvent
import android.view.View
import com.squareup.phrase.Phrase
import eu.pkgsoftware.babybuddywidgets.BaseFragment
import eu.pkgsoftware.babybuddywidgets.Constants
import eu.pkgsoftware.babybuddywidgets.DialogCallback
import eu.pkgsoftware.babybuddywidgets.R
import eu.pkgsoftware.babybuddywidgets.databinding.TimelineItemBinding
import eu.pkgsoftware.babybuddywidgets.networking.RequestCodeFailure
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.ChangeEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.FeedingEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.NoteEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.PumpingEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.SleepEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.TimeEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.models.TummyTimeEntry
import eu.pkgsoftware.babybuddywidgets.networking.babybuddy.serverTimeToClientTime
import eu.pkgsoftware.babybuddywidgets.timers.utils.feedingImageResourceFor
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.MalformedURLException
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

fun interpreteAmountValue(fragment: BaseFragment, amount: Double?): String {
    if (amount == null) {
        return ""
    }
    val nf = NumberFormat.getInstance()
    nf.maximumIntegerDigits = 3
    nf.minimumFractionDigits = 0
    val result = Phrase.from(
        fragment.resources, R.string.history_amount_timeline_pattern
    ).put("amount", nf.format(amount)).trim()

    return result + "\n"
}

class TimelineEntry(private val fragment: BaseFragment, private var _entry: TimeEntry?) {
    companion object {
        val DATE_FORMAT = DateFormat.getDateInstance(DateFormat.SHORT)
        val TIME_FORMAT = DateFormat.getTimeInstance(DateFormat.SHORT)
    }

    private val binding = TimelineItemBinding.inflate(fragment.mainActivity.layoutInflater)
    private var modifiedCallback: Runnable? = null
    private fun hideAllSubviews() {
        for (i in 0 until binding.viewGroup.childCount) {
            val c = binding.viewGroup.getChildAt(i)
            c.visibility = View.GONE
        }
    }

    var entry: TimeEntry?
        get() = _entry
        set(v) {
            if (v == _entry) {
                return
            }
            _entry = v
            updateUi()
        }

    val view: View
        get() = binding.root

    private fun updateUi() {
        when (val entry = _entry) {
            null -> binding.root.visibility = View.INVISIBLE
            is TummyTimeEntry -> configureTummyTime()
            is ChangeEntry -> configureChange()
            is SleepEntry -> configureSleep()
            is FeedingEntry -> configureFeeding(entry)
            is NoteEntry -> configureNote()
            is PumpingEntry -> configurePumping(entry)
            else -> configureDefaultView(entry)
        }
        binding.root.visibility = View.VISIBLE
    }

    init {
        binding.root.setOnLongClickListener { v: View? -> longClick() }
        binding.root.setOnTouchListener { v, event ->
            longClickStartStopHandler(v, event)
            false
        }
        binding.removeButton.setOnClickListener { v: View? -> removeClick() }
        updateUi()
    }

    private fun defaultPhraseFields(phrase: Phrase): Phrase {
        val localStartTime = serverTimeToClientTime(entry!!.start)
        val localEndTime = serverTimeToClientTime(entry!!.end)

        // Leverage Kotlins internal human readable format for Duration
        val eventDuration = java.time.Duration.between(
            localStartTime.toInstant(),
            localEndTime.toInstant(),
        ).toKotlinDuration()

        val startTime = TIME_FORMAT.format(localStartTime)
        val endTime = TIME_FORMAT.format(localEndTime)
        val timeRange = if (eventDuration < 30.seconds) startTime else "$startTime - $endTime ($eventDuration)"

        return phrase
            .putOptional("type", entry!!.appType)
            .putOptional("start_date", DATE_FORMAT.format(localStartTime))
            .putOptional("start_time", TIME_FORMAT.format(localStartTime))
            .putOptional("end_date", DATE_FORMAT.format(localEndTime))
            .putOptional("end_time", TIME_FORMAT.format(localEndTime))
            .putOptional("opt_time_range", timeRange)
            .putOptional("notes", entry!!.notes.trim())
    }

    private fun configureDefaultView(entry: TimeEntry) {
        hideAllSubviews()
        binding.genericType.text = entry.appType.take(1).uppercase(Locale.getDefault())
        binding.genericType.setBackgroundColor(entry.appType.asIconColor())
        binding.genericDetails.detailsText.text = defaultPhraseFields(
            Phrase.from("{notes}")
        ).trim()
        binding.defaultView.visibility = View.VISIBLE
    }

    private fun configureTummyTime() {
        hideAllSubviews()
        binding.tummyTimeDetails.dateTime.text =
            defaultPhraseFields(Phrase.from("{start_date}  {opt_time_range}")).trim()
        binding.tummyTimeDetails.detailsText.text =
            defaultPhraseFields(Phrase.from("{notes}")).trim()
        binding.tummyTimeView.visibility = View.VISIBLE
    }

    private fun configureChange() {
        hideAllSubviews()

        var amountString = ""

        (entry as ChangeEntry?)?.let { change ->
            binding.diaperWetImage.visibility =
                if (change.wet) View.VISIBLE else View.GONE
            binding.diaperSolidImage.visibility = if (change.solid) View.VISIBLE else View.GONE

            if (change.color.isNotEmpty()) {
                val colorEnumValue = Constants.SolidDiaperColorEnum.byPostName(change.color)
                fragment.resources.getColor(colorEnumValue.colorResId, null).let { color ->
                    binding.diaperSolidImage.setBackgroundColor(color)
                }
            } else {
                binding.diaperSolidImage.setBackgroundColor(TRANSPARENT)
            }

            amountString = interpreteAmountValue(fragment, change.amount)
        }
        binding.diaperDateTime.text =
            defaultPhraseFields(Phrase.from("{start_date}  {start_time}")).trim()
        binding.diaperText.text =
            defaultPhraseFields(Phrase.from("{amount}{notes}"))
                .put("amount", amountString)
                .trim()

        binding.diaperView.visibility = View.VISIBLE
    }

    private fun configureSleep() {
        hideAllSubviews()
        binding.sleepTimeDetails.dateTime.text =
            defaultPhraseFields(Phrase.from("{start_date}  {opt_time_range}"))
                .trim()
        binding.sleepTimeDetails.detailsText.text =
            defaultPhraseFields(Phrase.from("{notes}"))
                .trim()
        binding.sleepView.visibility = View.VISIBLE
    }

    private fun configureNote() {
        hideAllSubviews()
        binding.noteDetails.dateTime.text = defaultPhraseFields(Phrase.from("{start_date}  {start_time}")).trim()
        binding.noteDetails.detailsText.text = defaultPhraseFields(Phrase.from("{notes}")).trim()
        binding.noteTimeView.visibility = View.VISIBLE
    }

    private fun configurePumping(pumping: PumpingEntry) {
        hideAllSubviews()
        binding.pumpingDetails.dateTime.text =
            defaultPhraseFields(Phrase.from("{start_date}  {opt_time_range}")).trim()
        binding.pumpingDetails.detailsText.text =
            defaultPhraseFields(Phrase.from("{amount}{notes}"))
                .put("amount", interpreteAmountValue(fragment, pumping.amount))
                .trim()
        binding.pumpingTimeView.visibility = View.VISIBLE
    }

    private fun configureFeeding(feeding: FeedingEntry) {
        hideAllSubviews()
        binding.feedingBreastImage.setImageResource(
            feedingImageResourceFor(feeding.feedingType, feeding.feedingMethod)
        )
        binding.feedingBreastImage.visibility = View.VISIBLE
        binding.feedingDetails.dateTime.text =
            defaultPhraseFields(Phrase.from("{start_date}  {opt_time_range}"))
                .trim()
        binding.feedingDetails.detailsText.text =
            defaultPhraseFields(Phrase.from("{amount}{notes}"))
                .put("amount", interpreteAmountValue(fragment, feeding.amount))
                .trim()
        binding.feedingView.visibility = View.VISIBLE
    }

    private fun longClickStartStopHandler(v: View, event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            binding.longclickBubble.startGrow()
        } else if (event.action in listOf(
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_OUTSIDE,
            )
        ) {
            binding.longclickBubble.stopGrow()
        }
    }

    private fun longClick(): Boolean {
        binding.longclickBubble.stopGrow()
        val thisEntry = entry ?: return false
        val client = fragment.mainActivity.client.v2client
        return try {
            fragment.showUrlInBrowser(client.entryUserPath(thisEntry).toString())
            true
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            false
        }
    }

    private fun removeClick() {
        val thisEntry: TimeEntry = entry ?: return
        fragment.showQuestion(
            true,
            fragment.resources.getString(R.string.history_delete_title),
            defaultPhraseFields(
                Phrase.from(fragment.mainActivity, R.string.history_delete_question)
            ).trim(),
            fragment.resources.getString(R.string.history_delete_question_delete_button),
            fragment.resources.getString(R.string.history_delete_question_cancel_button),
            object : DialogCallback {
                override fun call(b: Boolean) {
                    if (!b) {
                        return
                    }
                    val client = fragment.mainActivity.client
                    fragment.mainActivity.scope.launch {
                        try {
                            client.v2client.deleteEntry(thisEntry)
                            this@TimelineEntry.entry = null
                            modifiedCallback?.run()
                        } catch (e: RequestCodeFailure) {
                            e.printStackTrace()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        )
    }

    fun setModifiedCallback(r: Runnable?) {
        modifiedCallback = r
    }
}

private fun Phrase.trim(): String = this.format().trim().toString()

/**
 * Create a "random", but repeatable, color for a string.
 * To be used as a generic background color for unknown entry types.
 */
private fun String.asIconColor(): Int =
    // Black = 0xFF000000, so we force 1st byte, the alpha channel, to be 0xFF, i.e. fully opaque.
    hashCode() or Color.BLACK