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

        if (getSupportActionBar() != null) getSupportActionBar().hide();
        rv = findViewById(R.id.recycler_grid);
        rvAdapter = new RVAdapter(instaClient, rv);

        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, displayWidth / 500));

        gridDataset = findViewById(R.id.grid_dataset);
        gridAction = findViewById(R.id.grid_action);
        ArrayAdapter<String> datasetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rvAdapter.getAllDatasets());
        datasetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridDataset.setAdapter(datasetAdapter);
        gridDataset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                rvAdapter.setCurrentDataset(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rvAdapter.getClickActions());
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridAction.setAdapter(actionAdapter);
        gridAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                rvAdapter.currentClickAction = RVAdapter.ClickAction.values()[i];
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }
}