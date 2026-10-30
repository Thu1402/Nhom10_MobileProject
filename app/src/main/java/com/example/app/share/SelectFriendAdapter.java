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
import com.example.app.ui.Ui;
import com.example.app.data.FriendRequestRepository;
import com.example.app.App;
import com.example.app.share.Friend;

import java.util.ArrayList;
import java.util.List;

public class SelectFriendAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend_select, parent, false);
        return new RecyclerView.ViewHolder(view) { };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        View itemView = holder.itemView;
        ImageView imgAvatar = itemView.findViewById(R.id.imgAvatar);
        TextView tvAvatarInitial = itemView.findViewById(R.id.tvAvatarInitial);
        TextView tvName = itemView.findViewById(R.id.tvName);
        TextView tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
        CheckBox cbSelect = itemView.findViewById(R.id.cbSelect);
        TextView friendAction = itemView.findViewById(R.id.btnFriendAction);
        Friend friend = friendList.get(position);
        tvName.setText(friend.getName());
        tvSubtitle.setText(friend.getSubtitle());
        if (friend.getAvatarUrl() != null && !friend.getAvatarUrl().trim().isEmpty()) {
            tvAvatarInitial.setVisibility(View.GONE);
            imgAvatar.setVisibility(View.VISIBLE);
            Glide.with(imgAvatar).load(Ui.imageModel(friend.getAvatarUrl())).circleCrop()
                    .placeholder(R.drawable.ic_launcher_foreground).error(R.drawable.ic_launcher_foreground)
                    .into(imgAvatar);
        } else {
            Glide.with(imgAvatar).clear(imgAvatar);
            imgAvatar.setVisibility(View.GONE);
            tvAvatarInitial.setText(initials(friend.getName()));
            tvAvatarInitial.setVisibility(View.VISIBLE);
        }

        cbSelect.setChecked(friend.isSelected());
        if (App.isAdmin()) {
            cbSelect.setVisibility(View.VISIBLE);
            boolean isFriend = FriendRequestRepository.ACCEPTED.equals(friend.getRelationshipStatus());
            cbSelect.setEnabled(isFriend || !friend.getId().isEmpty());
            friendAction.setText(friend.isLocked() ? "Mở khóa" : "Khóa tài khoản");
            friendAction.setEnabled(true);
            friendAction.setAlpha(1f);
            friendAction.setOnClickListener(v -> {
                if (friendActionListener != null) friendActionListener.onFriendAction(friend);
            });
            View.OnClickListener toggleSelection = v -> {
                friend.setSelected(!friend.isSelected());
                cbSelect.setChecked(friend.isSelected());
                if (listener != null) listener.onSelectionChanged(getSelectedFriends().size());
            };
            itemView.setOnClickListener(toggleSelection);
            cbSelect.setOnClickListener(toggleSelection);
            return;
        }
        String status = friend.getRelationshipStatus();
        boolean isFriend = FriendRequestRepository.ACCEPTED.equals(status);
        cbSelect.setVisibility(isFriend ? View.VISIBLE : View.GONE);
        if (isFriend) {
            friendAction.setText("Bạn bè");
            friendAction.setEnabled(false);
        } else if (FriendRequestRepository.OUTGOING.equals(status)) {
            friendAction.setText("Đã gửi");
            friendAction.setEnabled(false);
        } else if (FriendRequestRepository.INCOMING.equals(status)) {
            friendAction.setText("Có lời mời");
            friendAction.setEnabled(false);
        } else {
            friendAction.setText("Kết bạn");
            friendAction.setEnabled(true);
        }
        friendAction.setAlpha(friendAction.isEnabled() ? 1f : 0.65f);
        friendAction.setOnClickListener(v -> {
            if (friendActionListener != null) friendActionListener.onFriendAction(friend);
        });

        View.OnClickListener toggleSelection = v -> {
            if (!isFriend) return;
            friend.setSelected(!friend.isSelected());
            cbSelect.setChecked(friend.isSelected());
            if (listener != null) {
                listener.onSelectionChanged(getSelectedFriends().size());
            }
        };
        itemView.setOnClickListener(toggleSelection);
        cbSelect.setOnClickListener(toggleSelection);
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

}

