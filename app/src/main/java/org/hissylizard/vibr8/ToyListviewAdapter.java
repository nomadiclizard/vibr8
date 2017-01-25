package org.hissylizard.vibr8;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.hissylizard.vibr8.service.LovenseToy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by sungazer on 23/01/2017.
 */


// TODO how the fuck do complex listview items work?????
public class ToyListViewAdapter extends BaseAdapter {

    private List<HashMap<String, String>> data;
    private LayoutInflater inflater = null;

    public ToyListViewAdapter(ArrayList data) {
        this.data = data;
    }


    public int getCount() {
        return data.size();
    }


    public Object getItem(int position) {
        return position;
    }


    public long getItemId(int position) {
        return position;
    }


    public View getView(int position, View convertView, ViewGroup parent) {
        // TODO?
        return convertView;
    }
}
