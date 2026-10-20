package J2.giaodien2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.giaodien2.Friend;

import java.util.ArrayList;
import java.util.List;

public class SelectFriendAdapter extends RecyclerView.Adapter<SelectFriendAdapter.ViewHolder> {

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    private Context context;
    private List<Friend> friendList;
    private OnSelectionChangeListener listener;

    public SelectFriendAdapter(Context context, List<Friend> friendList, OnSelectionChangeListener listener) {
        this.context = context;
        this.friendList = friendList;
        this.listener = listener;
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
        if (friend.getAvatarResId() != 0) {
            holder.imgAvatar.setImageResource(friend.getAvatarResId());
        }

        holder.cbSelect.setChecked(friend.isSelected());

        holder.itemView.setOnClickListener(v -> {
            friend.setSelected(!friend.isSelected());
            holder.cbSelect.setChecked(friend.isSelected());
            if (listener != null) {
                listener.onSelectionChanged(getSelectedFriends().size());
            }
        });
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvName, tvSubtitle;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }
    }
}