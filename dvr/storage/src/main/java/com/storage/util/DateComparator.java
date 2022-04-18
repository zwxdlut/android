package com.storage.util;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

public class DateComparator implements Comparator<String> {
    private int order = Constant.OrderType.DESCENDING;
    private SimpleDateFormat simpleDateFormat = null;

    public DateComparator(@NotNull SimpleDateFormat sdf, int order) {
        simpleDateFormat = sdf;
        this.order = order;
    }

    @Override
    public int compare(String o1, String o2) {
        Date date1 = null;
        Date date2 = null;

        try {
            date1 = simpleDateFormat.parse(o1);
            date2 = simpleDateFormat.parse(o2);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if (Constant.OrderType.ASCENDING == order) {
            if (null != date1) {
                return date1.compareTo(date2);
            } else {
                return o1.compareTo(o2);
            }

        } else if (Constant.OrderType.DESCENDING == order) {
            if (null != date2) {
                return date2.compareTo(date1);
            } else {
                return o2.compareTo(o1);
            }
        } else {
            return 1;
        }
    }
}