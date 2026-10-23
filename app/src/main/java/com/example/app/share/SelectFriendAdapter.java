package com.example.app.share;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.app.R;
import com.example.app.data.FriendRequestRepository;
import com.example.app.share.Friend;

import java.util.ArrayList;
import java.util.List;

public class SelectFriendAdapter extends RecyclerView.Adapter<SelectFriendAdapter.ViewHolder> {

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    public interface OnFriendActionListener {
        void onFriendAction(Friend friend);
    }

    private Context context;
    private List<Friend> friendList;
    private OnSelectionChangeListener listener;
    private OnFriendActionListener friendActionListener;

    public SelectFriendAdapter(Context context, List<Friend> friendList,
                               OnSelectionChangeListener listener,
                               OnFriendActionListener friendActionListener) {
        this.context = context;
        this.friendList = friendList;
        this.listener = listener;
        this.friendActionListener = friendActionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Friend friend = friendList.get(position);
        holder.tvName.setText(friend.getName());
        holder.tvSubtitle.setText(friend.getSubtitle());
        if (friend.getAvatarUrl() != null && !friend.getAvatarUrl().trim().isEmpty()) {
            holder.tvAvatarInitial.setVisibility(View.GONE);
            holder.imgAvatar.setVisibility(View.VISIBLE);
            Glide.with(holder.imgAvatar).load(friend.getAvatarUrl()).circleCrop()
                    .placeholder(R.drawable.ic_launcher_foreground).error(R.drawable.ic_launcher_foreground)
                    .into(holder.imgAvatar);
        } else {
            Glide.with(holder.imgAvatar).clear(holder.imgAvatar);
            holder.imgAvatar.setVisibility(View.GONE);
            holder.tvAvatarInitial.setText(initials(friend.getName()));
            holder.tvAvatarInitial.setVisibility(View.VISIBLE);
        }

        holder.cbSelect.setChecked(friend.isSelected());
        String status = friend.getRelationshipStatus();
        boolean isFriend = FriendRequestRepository.ACCEPTED.equals(status);
        holder.cbSelect.setVisibility(isFriend ? View.VISIBLE : View.GONE);
        if (isFriend) {
            holder.friendAction.setText("Bạn bè");
            holder.friendAction.setEnabled(false);
        } else if (FriendRequestRepository.OUTGOING.equals(status)) {
            holder.friendAction.setText("Đã gửi");
            holder.friendAction.setEnabled(false);
        } else if (FriendRequestRepository.INCOMING.equals(status)) {
            holder.friendAction.setText("Có lời mời");
            holder.friendAction.setEnabled(false);
        } else {
            holder.friendAction.setText("Kết bạn");
            holder.friendAction.setEnabled(true);
        }
        holder.friendAction.setAlpha(holder.friendAction.isEnabled() ? 1f : 0.65f);
        holder.friendAction.setOnClickListener(v -> {
            if (friendActionListener != null) friendActionListener.onFriendAction(friend);
        });

        View.OnClickListener toggleSelection = v -> {
            if (!isFriend) return;
            friend.setSelected(!friend.isSelected());
            holder.cbSelect.setChecked(friend.isSelected());
            if (listener != null) {
                listener.onSelectionChanged(getSelectedFriends().size());
            }
        };
        holder.itemView.setOnClickListener(toggleSelection);
        holder.cbSelect.setOnClickListener(toggleSelection);
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public int getItemCount() {
        return friendList.size();
    }

    public List<Friend> getSelectedFriends() {
        List<Friend> selected = new ArrayList<>();
        for (Friend f : friendList) {
            if (f.isSelected()) selected.add(f);
        }
        return selected;
    }

    public void replaceFriends(List<Friend> friends) {
        friendList.clear();
        friendList.addAll(friends);
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvName, tvSubtitle, tvAvatarInitial;
        CheckBox cbSelect;
        TextView friendAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            tvAvatarInitial = itemView.findViewById(R.id.tvAvatarInitial);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            friendAction = itemView.findViewById(R.id.btnFriendAction);
        }
    }
}

