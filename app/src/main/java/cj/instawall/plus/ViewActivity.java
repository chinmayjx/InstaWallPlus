package cj.instawall.plus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

public class ViewActivity extends AppCompatActivity {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    RecyclerView rv;
    Spinner gridFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);
        if(getSupportActionBar() != null) getSupportActionBar().hide();
        rv = findViewById(R.id.recycler_grid);
        gridFilter = findViewById(R.id.grid_filter);
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, new String[]{"abcd","abcd","abcd","abcd"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridFilter.setAdapter(adapter);
        gridFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d(TAG, "onItemSelected: " + i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                Log.d(TAG, "onNothingSelected: ");
            }
        });

        try {
            instaClient = InstaClient.getInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        rv.setAdapter(new RVAdapter(instaClient));
        rv.setLayoutManager(new GridLayoutManager(this, 2));
    }
}

class RVAdapter extends RecyclerView.Adapter {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    List<Bitmap> bitmaps;
    List<Boolean> requested;
    Handler handler;

    public RVAdapter(InstaClient instaClient) {
        this.instaClient = instaClient;
        this.handler = new Handler();
        bitmaps = Arrays.asList(new Bitmap[100]);
        requested = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requested.add(false);
        }
    }

    public static class RVHolder extends RecyclerView.ViewHolder {
        ImageView iv;

        public RVHolder(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.grid_iv);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.grid_item, parent, false);
        return new RVHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RVHolder rvHolder = (RVHolder) holder;
        if (bitmaps.get(position) != null) {
            rvHolder.iv.setImageBitmap(bitmaps.get(position));
        } else {
            rvHolder.iv.setImageBitmap(null);
            if (!requested.get(position)) {
                requested.set(position, true);
                instaClient.act_getRandomImageAsync(path -> {
                    Bitmap b = BitmapFactory.decodeFile(path.toString());
                    int sw = 800;
                    int sh = (int) (sw * ((float) b.getHeight() / b.getWidth()));
                    b = Bitmap.createScaledBitmap(b, sw, sh, false);
                    bitmaps.set(position, b);
                    handler.post(() -> notifyItemChanged(position));
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return 100;
    }
}