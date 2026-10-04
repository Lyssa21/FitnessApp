package com.example.fitnessapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.fitnessapp.model.Workout

class WorkoutAdapter(
    private val workouts: MutableList<Workout>,
    private val onLongClick: (Workout) -> Unit
) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

    class WorkoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.workoutCardView)
        val icon: TextView = itemView.findViewById(R.id.workoutIconTextView)
        val name: TextView = itemView.findViewById(R.id.workoutNameTextView)
        val details: TextView = itemView.findViewById(R.id.workoutDetailsTextView)
        val location: TextView = itemView.findViewById(R.id.workoutLocationTextView)
        val date: TextView = itemView.findViewById(R.id.workoutDateTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout, parent, false)
        return WorkoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        val workout = workouts[position]
        holder.icon.text = workout.icon
        holder.name.text = workout.activityName
        holder.details.text = "${workout.durationMinutes} min  •  ${workout.calories} kcal"
        holder.date.text = workout.date
        holder.location.text = if (workout.latitude != null && workout.longitude != null) {
            "📍 %.4f, %.4f".format(workout.latitude, workout.longitude)
        } else {
            "No location"
        }

        val colour = when (workout.activityName) {
            "Running" -> R.color.pastel_peach
            "Cycling" -> R.color.pastel_mint
            "Weightlifting" -> R.color.pastel_lilac
            else -> R.color.pastel_blue
        }
        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, colour))
        holder.itemView.setOnLongClickListener {
            onLongClick(workout)
            true
        }
    }

    override fun getItemCount(): Int = workouts.size

    fun replaceItems(newItems: List<Workout>) {
        workouts.clear()
        workouts.addAll(newItems)
        notifyDataSetChanged()
    }
}
