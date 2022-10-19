package cj.instawall.plus;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

public class ViewActivity extends AppCompatActivity {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    RecyclerView rv;
    Spinner gridDataset, gridAction;
    RVAdapter rvAdapter;
    int displayWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);
        try {
            instaClient = InstaClient.getInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        displayWidth = displayMetrics.widthPixels;

        FloatingActionButton fab = findViewById(R.id.grid_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Here's a Snackbar", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });


        if (getSupportActionBar() != null) getSupportActionBar().hide();
        rv = findViewById(R.id.recycler_grid);
        rvAdapter = new RVAdapter(instaClient, rv);

        rvAdapter.onEnterSelected = () -> fab.setVisibility(View.VISIBLE);
        rvAdapter.onExitSelected = () -> fab.setVisibility(View.GONE);

        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, displayWidth / 500));

        gridDataset = findViewById(R.id.grid_dataset);
        gridAction = findViewById(R.id.grid_action);
        ArrayAdapter<RVAdapter.Dataset> datasetAdapter = new ArrayAdapter<RVAdapter.Dataset>(this, android.R.layout.simple_spinner_item, RVAdapter.Dataset.values());
        datasetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridDataset.setAdapter(datasetAdapter);

        ArrayAdapter<RVAdapter.ClickAction> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rvAdapter.currentClickActions);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridAction.setAdapter(actionAdapter);


        gridDataset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                rvAdapter.setCurrentDataset(i);
                actionAdapter.notifyDataSetChanged();
                gridAction.setSelection(0);
                rvAdapter.setCurrentClickAction(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        gridAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                rvAdapter.setCurrentClickAction(adapterView.getSelectedItemPosition());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

    }

    @Override
    public void onBackPressed() {
        if (rvAdapter.selected.size() > 0) {
            rvAdapter.clearSelection();
        } else {
            super.onBackPressed();
        }
    }
}