package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TasbihAdapter extends RecyclerView.Adapter<TasbihAdapter.ViewHolder> {

    Context context;
    List<TasbihModel> list;
    Runnable saveCallback;

    public TasbihAdapter(Context context,
                         List<TasbihModel> list,
                         Runnable saveCallback) {
        this.context = context;
        this.list = list;
        this.saveCallback = saveCallback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tasbih, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {

        TasbihModel t = list.get(position);
        h.tvName.setText(t.name);
        
        java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
        h.tvCount.setText(formatter.format(t.count));

        // Click → open TasbihCounterActivity
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(context, TasbihCounterActivity.class);
            i.putExtra("tasbih", t);
            context.startActivity(i);
        });

        // Long Click → Professional Options Dialog
        h.itemView.setOnLongClickListener(v -> {
            int currentPos = h.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return true;

            android.app.Dialog dialog = new android.app.Dialog(context);
            dialog.setContentView(R.layout.dialog_tasbih_options);
            
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            TextView tvTitle = dialog.findViewById(R.id.tvOptionTitle);
            tvTitle.setText(t.name); // Set current tasbih name as title

            // Reset Listener
            dialog.findViewById(R.id.llReset).setOnClickListener(v1 -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    list.get(pos).count = 0;
                    notifyItemChanged(pos);
                    if (saveCallback != null) saveCallback.run();
                    android.widget.Toast.makeText(context, "Counter has been reset", android.widget.Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            });

            // Delete Listener
            dialog.findViewById(R.id.llDelete).setOnClickListener(v2 -> {
                dialog.dismiss();
                // Professional Delete Confirmation
                new AlertDialog.Builder(context)
                        .setTitle("Delete Tasbih?")
                        .setMessage("Are you sure you want to permanently delete this tasbih?")
                        .setPositiveButton("Delete", (d, w) -> {
                            int posToDelete = h.getAdapterPosition();
                            if (posToDelete == RecyclerView.NO_POSITION) return;
                            list.remove(posToDelete);
                            notifyItemRemoved(posToDelete);
                            notifyItemRangeChanged(posToDelete, list.size());
                            if (saveCallback != null) saveCallback.run();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            dialog.findViewById(R.id.btnOptionCancel).setOnClickListener(v3 -> dialog.dismiss());

            dialog.show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCount;
        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvCount = v.findViewById(R.id.tvCount);
        }
    }
}
