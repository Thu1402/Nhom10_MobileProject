package com.example.mobile_nhom10.data;

import com.example.mobile_nhom10.model.Place;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AppData {
    private AppData() {}

    public static final List<Place> PLACES = new ArrayList<>(Arrays.asList(
            new Place(
                    "Hồ Xanh Mây",
                    "Thiên nhiên",
                    "Đồi Thông, Đà Lạt",
                    "2,4 km",
                    "https://images.unsplash.com/photo-1759476313158-5f5064f66b8a?crop=entropy&cs=tinysrgb&fit=crop&fm=jpg&q=84&w=1080"
            ),
            new Place(
                    "Nép Coffee",
                    "Cà phê",
                    "12 Trần Phú, Đà Lạt",
                    "850 m",
                    "https://images.unsplash.com/photo-1778653202386-06d020d905df?crop=entropy&cs=tinysrgb&fit=crop&fm=jpg&q=84&w=1080"
            ),
            new Place(
                    "Hiên Nhà Vàng",
                    "Khám phá",
                    "Phố cổ Hội An",
                    "4,1 km",
                    "https://images.unsplash.com/photo-1504699439244-a7e34870c35d?crop=entropy&cs=tinysrgb&fit=crop&fm=jpg&q=84&w=1080"
            )
    ));

    public static final String[][] FRIENDS = {
            {"MA", "Minh Anh", "@minhanh", "Đang hoạt động"},
            {"QH", "Quang Huy", "@qhuy", "Hoạt động 2 giờ trước"},
            {"TV", "Thảo Vy", "@thaovy", "Đang hoạt động"},
            {"GL", "Gia Linh", "@linhgia", "Hoạt động hôm qua"}
    };
}
