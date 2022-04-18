package com.mxnavi.dvr.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.mxnavi.dvr.R;

public class DialogUtil {
    public static void showNormalDialog(Context context, CharSequence title, CharSequence msg,
                                        final CharSequence confirm, final View.OnClickListener confirmListener,
                                        final CharSequence cancel, final View.OnClickListener cancelListener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_util,null);
        final AlertDialog dialog = new AlertDialog.Builder(context).create();

        ((TextView)view.findViewById(R.id.tv_title)).setText(title);
        ((TextView)view.findViewById(R.id.btn_confirm)).setText(confirm);

        if (null == msg || 0 == msg.length()) {
            ((TextView)view.findViewById(R.id.tv_msg)).setVisibility(View.GONE);
        } else {
            ((TextView)view.findViewById(R.id.tv_msg)).setText(msg);
        }

        if (null == cancel || 0 == cancel.length()) {
            ((TextView)view.findViewById(R.id.btn_cancel)).setVisibility(View.GONE);
        } else {
            ((TextView)view.findViewById(R.id.btn_cancel)).setText(cancel);
        }

        ((TextView)view.findViewById(R.id.btn_confirm)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != confirmListener) {
                    confirmListener.onClick(v);
                    dialog.dismiss();
                }
            }
        });

        ((TextView)view.findViewById(R.id.btn_cancel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != cancelListener) {
                    cancelListener.onClick(v);
                    dialog.dismiss();
                }
            }
        });

        dialog.setView(view);
        dialog.show();
    }
}
