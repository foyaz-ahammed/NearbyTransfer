package com.exam.nearby;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.exam.nearby.databinding.RowContactBinding;

import java.util.function.Consumer;

public class ConnectorListAdapter extends ListAdapter<Contact, ConnectorListAdapter.ViewHolder> {
    static final MyDiffCallback diffCallback = new MyDiffCallback();

    private Consumer<Contact> contactItemUpdateListener;

    public ConnectorListAdapter() {
        super(diffCallback);
    }

    public void setContactItemUpdateListener(Consumer<Contact> listener) {
        contactItemUpdateListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RowContactBinding binding = RowContactBinding.inflate(LayoutInflater.from(parent.getContext()));
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact item = getItem(position);
        holder.bind(item);
    }

    static class MyDiffCallback extends DiffUtil.ItemCallback<Contact> {
        @Override
        public boolean areItemsTheSame(@NonNull Contact oldItem, @NonNull Contact newItem) {
            return oldItem.isSame(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Contact oldItem, @NonNull Contact newItem) {
            return oldItem.getColor() == newItem.getColor();
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private RowContactBinding binding;

        public ViewHolder(RowContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Contact item) {
            // Set color
            ColorStateList colorStateList = ContextCompat.getColorStateList(binding.getRoot().getContext(), item.getColor());
            binding.circle.setImageTintList(colorStateList);

            // Set text of name
            binding.name.setText(item.getName());

            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    binding.checkbox.setChecked(!binding.checkbox.isChecked());
                }
            });

            binding.checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    item.toggleChecked();
                    if(contactItemUpdateListener != null) {
                        contactItemUpdateListener.accept(item);
                    }
                }
            });
        }
    }
}
