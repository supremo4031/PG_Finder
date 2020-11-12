package com.arpan.pgfinder.Activities;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.arpan.pgfinder.R;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.common.base.Optional;
import com.tomtom.online.sdk.common.location.LatLng;
import com.tomtom.online.sdk.map.BaseMarkerBalloon;
import com.tomtom.online.sdk.map.Icon;
import com.tomtom.online.sdk.map.MapFragment;
import com.tomtom.online.sdk.map.Marker;
import com.tomtom.online.sdk.map.MarkerBuilder;
import com.tomtom.online.sdk.map.OnMapReadyCallback;
import com.tomtom.online.sdk.map.Route;
import com.tomtom.online.sdk.map.RouteBuilder;
import com.tomtom.online.sdk.map.SingleLayoutBalloonViewAdapter;
import com.tomtom.online.sdk.map.TomtomMap;
import com.tomtom.online.sdk.map.TomtomMapCallback;
import com.tomtom.online.sdk.routing.OnlineRoutingApi;
import com.tomtom.online.sdk.routing.RoutingApi;
import com.tomtom.online.sdk.routing.data.FullRoute;
import com.tomtom.online.sdk.routing.data.RouteQuery;
import com.tomtom.online.sdk.routing.data.RouteQueryBuilder;
import com.tomtom.online.sdk.routing.data.RouteResponse;
import com.tomtom.online.sdk.routing.data.RouteType;
import com.tomtom.online.sdk.search.OnlineSearchApi;
import com.tomtom.online.sdk.search.SearchApi;
import com.tomtom.online.sdk.search.data.alongroute.AlongRouteSearchQueryBuilder;
import com.tomtom.online.sdk.search.data.alongroute.AlongRouteSearchResponse;
import com.tomtom.online.sdk.search.data.alongroute.AlongRouteSearchResult;
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchQueryBuilder;
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchResponse;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback,
        TomtomMapCallback.OnMapLongClickListener {

    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle actionBarDrawerToggle;

    // Tomtom
    private TomtomMap tomtomMap;
    private SearchApi searchApi;
    private RoutingApi routingApi;
    private Route route;
    private LatLng departurePosition;
    private LatLng destinationPosition;
    private LatLng wayPointPosition;
    private Icon departureIcon;
    private Icon destinationIcon;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setAllViews();
        initTomTomServices();
        initUIViews();
        setupUIViewListeners();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem menuItem = menu.findItem(R.id.app_bar_search);
        SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setQueryHint("Type here to search");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                handleSearchClick(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        return true;
    }

    private void handleSearchClick(String query) {
//        Toast.makeText(MainActivity.this, "fuck", Toast.LENGTH_SHORT).show();
        if (isRouteSet()) {
            Optional<CharSequence> description = Optional.fromNullable(query);
            if (isWayPointPositionSet()) {
                tomtomMap.clear();
                drawRoute(departurePosition, destinationPosition);
            }
            String textToSearch = query;
            if (!textToSearch.isEmpty()) {
                tomtomMap.removeMarkers();
                searchAlongTheRoute(route, textToSearch);
            }
        }
    }

    private boolean isRouteSet() {
        return route != null;
    }

    private boolean isWayPointPositionSet() {
        return wayPointPosition != null;
    }
    private void searchAlongTheRoute(Route route, final String textToSearch) {
        Toast.makeText(MainActivity.this, "never suck", Toast.LENGTH_SHORT).show();
        final Integer MAX_DETOUR_TIME = 1000;
        final Integer QUERY_LIMIT = 10;

        searchApi.alongRouteSearch(new AlongRouteSearchQueryBuilder(textToSearch, route.getCoordinates(), MAX_DETOUR_TIME).withLimit(QUERY_LIMIT).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<AlongRouteSearchResponse>() {
                    @Override
                    public void onSuccess(AlongRouteSearchResponse response) {
                        displaySearchResults(response.getResults());
                    }

                    private void displaySearchResults(List<AlongRouteSearchResult> results) {
                        if (!results.isEmpty()) {
                            for (AlongRouteSearchResult result : results) {
                                createAndDisplayCustomMarker(result.getPosition(), result);
                            }
                            tomtomMap.zoomToAllMarkers();
                        } else {
                            Toast.makeText(MainActivity.this, String.format(getString(R.string.no_search_results), textToSearch), Toast.LENGTH_LONG).show();
                        }
                    }

                    private void createAndDisplayCustomMarker(LatLng position, AlongRouteSearchResult result) {
                        String address = result.getAddress().getFreeformAddress();
                        String poiName = result.getPoi().getName();

                        BaseMarkerBalloon markerBalloonData = new BaseMarkerBalloon();
                        markerBalloonData.addProperty(getString(R.string.poi_name_key), poiName);
                        markerBalloonData.addProperty(getString(R.string.address_key), address);

                        MarkerBuilder markerBuilder = new MarkerBuilder(position)
                                .markerBalloon(markerBalloonData)
                                .shouldCluster(true);
                        tomtomMap.addMarker(markerBuilder);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        handleApiError(e);
                    }
                });
    }

    private void setAllViews() {

        toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        actionBarDrawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.openNavDrawer,
                R.string.closeNavDrawer
        );

        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    public void onMapReady(@NonNull TomtomMap tomtomMap) {
        this.tomtomMap = tomtomMap;
        this.tomtomMap.setMyLocationEnabled(true);
        this.tomtomMap.addOnMapLongClickListener(this);
        this.tomtomMap.getMarkerSettings().setMarkersClustering(true);
        this.tomtomMap.getMarkerSettings().setMarkerBalloonViewAdapter(createCustomViewAdapter());
    }

    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {
        if (isDeparturePositionSet() && isDestinationPositionSet()) {
            clearMap();
        } else {
            handleLongClick(latLng);
        }
    }

    private void handleLongClick(@NonNull LatLng latLng) {
        searchApi.reverseGeocoding(new ReverseGeocoderSearchQueryBuilder(latLng.getLatitude(), latLng.getLongitude()).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<ReverseGeocoderSearchResponse>() {
                    @Override
                    public void onSuccess(ReverseGeocoderSearchResponse response) {
                        processResponse(response);
                    }

                    @Override
                    public void onError(Throwable e) {
                        handleApiError(e);
                    }

                    private void processResponse(ReverseGeocoderSearchResponse response) {
                        if (response.hasResults()) {
                            processFirstResult(response.getAddresses().get(0).getPosition());
                        }
                        else {
                            Toast.makeText(MainActivity.this, getString(R.string.geocode_no_results), Toast.LENGTH_SHORT).show();
                        }
                    }

                    private void processFirstResult(LatLng geocodedPosition) {
                        if (!isDeparturePositionSet()) {
                            setAndDisplayDeparturePosition(geocodedPosition);
                        } else {
                            destinationPosition = geocodedPosition;
                            tomtomMap.removeMarkers();
                            drawRoute(departurePosition, destinationPosition);
                        }
                    }

                    private void setAndDisplayDeparturePosition(LatLng geocodedPosition) {
                        departurePosition = geocodedPosition;
                        createMarkerIfNotPresent(departurePosition, departureIcon);
                    }
                });
    }

    private SingleLayoutBalloonViewAdapter createCustomViewAdapter() {
        return new SingleLayoutBalloonViewAdapter(R.layout.marker_custom_balloon) {
            @Override
            public void onBindView(View view, final Marker marker, BaseMarkerBalloon baseMarkerBalloon) {
                Button btnAddWayPoint = view.findViewById(R.id.btn_balloon_waypoint);
                TextView textViewPoiName = view.findViewById(R.id.textview_balloon_poiname);
                TextView textViewPoiAddress = view.findViewById(R.id.textview_balloon_poiaddress);
                textViewPoiName.setText(baseMarkerBalloon.getStringProperty(getApplicationContext().getString(R.string.poi_name_key)));
                textViewPoiAddress.setText(baseMarkerBalloon.getStringProperty(getApplicationContext().getString(R.string.address_key)));
                btnAddWayPoint.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setWayPoint(marker);
                    }

                    private void setWayPoint(Marker marker) {
                        wayPointPosition = marker.getPosition();
                        tomtomMap.clearRoute();
                        drawRouteWithWayPoints(departurePosition, destinationPosition, new LatLng[] {wayPointPosition});
                        marker.deselect();
                    }
                });
            }
        };
    }

    private boolean isDestinationPositionSet() {
        return destinationPosition != null;
    }

    private boolean isDeparturePositionSet() {
        return departurePosition != null;
    }

    private void handleApiError(Throwable e) {
        Toast.makeText(MainActivity.this, getString(R.string.api_response_error, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
    }

    private RouteQuery createRouteQuery(LatLng start, LatLng stop, LatLng[] wayPoints) {
        return (wayPoints != null) ?
                new RouteQueryBuilder(start, stop).withWayPoints(wayPoints).withRouteType(RouteType.FASTEST).build() :
                new RouteQueryBuilder(start, stop).withRouteType(RouteType.FASTEST).build();
    }

    private void drawRoute(LatLng start, LatLng stop) {
        wayPointPosition = null;
        drawRouteWithWayPoints(start, stop, null);
    }

    private void drawRouteWithWayPoints(LatLng start, LatLng stop, LatLng[] wayPoints) {
        RouteQuery routeQuery = createRouteQuery(start, stop, wayPoints);
        routingApi.planRoute(routeQuery)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<RouteResponse>() {

                    @Override
                    public void onSuccess(RouteResponse routeResponse) {
                        displayRoutes(routeResponse.getRoutes());
                        tomtomMap.displayRoutesOverview();
                    }

                    private void displayRoutes(List<FullRoute> routes) {
                        for (FullRoute fullRoute : routes) {
                            route = tomtomMap.addRoute(new RouteBuilder(
                                    fullRoute.getCoordinates()).startIcon(departureIcon).endIcon(destinationIcon));
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        handleApiError(e);
                        clearMap();
                    }
                });
    }

    private void createMarkerIfNotPresent(LatLng position, Icon icon) {
        Optional<Marker> optionalMarker = tomtomMap.findMarkerByPosition(position);
        if (!optionalMarker.isPresent()) {
            tomtomMap.addMarker(new MarkerBuilder(position)
                    .icon(icon));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        this.tomtomMap.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initTomTomServices() {
        MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mapFragment.getAsyncMap(this);

        searchApi = OnlineSearchApi.create(this);
        routingApi = OnlineRoutingApi.create(this);

    }

    private void initUIViews() {
        departureIcon = Icon.Factory.fromResources(MainActivity.this, R.drawable.ic_map_route_departure);
        destinationIcon = Icon.Factory.fromResources(MainActivity.this, R.drawable.ic_map_route_destination);
    }

    private void setupUIViewListeners() {

    }

    private void clearMap() {
        tomtomMap.clear();
        departurePosition = null;
        destinationPosition = null;
        route = null;
    }
}