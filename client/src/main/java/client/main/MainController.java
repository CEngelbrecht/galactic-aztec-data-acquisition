package client.main;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;

import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

import com.badlogic.gdx.math.Vector3;
import com.lynden.gmapsfx.GoogleMapView;
import com.lynden.gmapsfx.MapComponentInitializedListener;
import com.lynden.gmapsfx.javascript.object.GoogleMap;
import com.lynden.gmapsfx.javascript.object.LatLong;
import com.lynden.gmapsfx.javascript.object.MapOptions;
import com.lynden.gmapsfx.javascript.object.MapTypeIdEnum;
import com.lynden.gmapsfx.javascript.object.Marker;
import com.lynden.gmapsfx.javascript.object.MarkerOptions;

import edu.sdsu.rocket.core.models.Pressures;
import edu.sdsu.rocket.core.models.Sensors;
import edu.sdsu.rocket.core.net.SensorClient;
import edu.sdsu.rocket.core.net.SensorClient.Mode;
import eu.hansolo.enzo.common.Section;
import eu.hansolo.enzo.gauge.Gauge;
import eu.hansolo.enzo.gauge.GaugeBuilder;

@SuppressWarnings("deprecation")
public class MainController {
	
	private static final double GAUGE_WIDTH  = 316;
	private static final double GAUGE_HEIGHT = 316;
	
	private static final double CHART_WIDTH  = GAUGE_WIDTH * 2;
	private static final double CHART_HEIGHT = 190;
	
	private static final long NANOSECONDS_PER_MILLISECOND = 1000000L;
	private static final long MILLISECONDS_PER_SECOND     = 1000L;
	
	private static final long GPS_UPATE_INTERVAL_MILLIS = 1L * MILLISECONDS_PER_SECOND; // 1 Hz
	
	private static final boolean DEBUG_SENSORS = false;
	
	private static final String CONNECT    = "Connect";
	private static final String DISCONNECT = "Disconnect";
	
	private static final int PORT = 4444;
	private final Sensors local = new Sensors();
	private final Sensors remote = new Sensors();
	private final SensorClient client = new SensorClient(local, remote);
	private Thread pingThread;
	
	@FXML private TextField hostTextField;
	@FXML private Button connectButton;
	@FXML private Slider frequencySlider;
	@FXML private Label frequencyLabel;
	@FXML private ToggleGroup sensorsGroup;
	@FXML private ToggleButton localButton;
	@FXML private ToggleButton remoteButton;
	@FXML private Label latencyLabel;
	@FXML private Label signalLabel;
	@FXML private Label gpsFixLabel;
	@FXML private Label gpsSatellitesLabel;
	@FXML private FlowPane gaugePane;
	
	@FXML
    private GoogleMapView mapView;
	private GoogleMap map;
	
	private Gauge lox;
	private Gauge kerosene;
	private Gauge helium;
	private Gauge motor;
	
	private int chartIndex;
	private static final int ACCELEROMETER_DATA_POINTS = 50;
	private static final int GYROSCOPE_DATA_POINTS     = 50;
	private static final int MAGNETOMETER_DATA_POINTS  = 50;
	private static final int BAROMETER_DATA_POINTS     = 50;
	
	private NumberAxis accelerometerX;
	private Series<Number, Number> accelerometerXData = new XYChart.Series<Number, Number>();
	private Series<Number, Number> accelerometerYData = new XYChart.Series<Number, Number>();
	private Series<Number, Number> accelerometerZData = new XYChart.Series<Number, Number>();
	
	private NumberAxis gyroscopeX;
	private Series<Number, Number> gyroscopeXData = new XYChart.Series<Number, Number>();
	private Series<Number, Number> gyroscopeYData = new XYChart.Series<Number, Number>();
	private Series<Number, Number> gyroscopeZData = new XYChart.Series<Number, Number>();
	
	private NumberAxis magnetometerX;
	private Series<Number, Number> magnetometerXData = new XYChart.Series<Number, Number>();
	private Series<Number, Number> magnetometerYData = new XYChart.Series<Number, Number>();
	private Series<Number, Number> magnetometerZData = new XYChart.Series<Number, Number>();
	
	private NumberAxis barometerX;
	private Series<Number, Number> barometerPressureData = new XYChart.Series<Number, Number>();
	
	private Marker marker;
	private long lastGpsUpdateMillis = System.currentTimeMillis();

	private static final Vector3 tmpVec = new Vector3();

	/**
	 * Constructor for the controller.
	 * 
	 * Called prior to the initialize() method.
	 */
	public MainController() {
		client.setListener(new SensorClient.SensorClientListener() {
			@Override
			public void onPingResponse(final long latency) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						updateLatency((float) latency / NANOSECONDS_PER_MILLISECOND);
					}
				});
			}
			
			@Override
			public void onSensorsUpdated() {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						Sensors sensors;
						Toggle selected = sensorsGroup.getSelectedToggle();
						if (remoteButton.equals(selected)) {
							sensors = remote;
						} else {
							sensors = local;
						}
						updateSensors(sensors);
						updateGPS();
					}
				});
			}
		});
	}
	
	/**
	 * Initialize the controller.
	 * 
	 * Automatically called after the FXML view has been loaded.
	 * 
	 * Configures the child Life Line controller/view.
	 */
	@FXML
	private void initialize() {
		frequencySlider.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            	int value = newValue.intValue();
            	frequencyLabel.setText(value + " Hz");
            	client.setFrequency(value);
            }
        });
		
		createSensors();
		
		mapView.addMapInializedListener(new MapComponentInitializedListener() {
			@Override
			public void mapInitialized() {
		        MapOptions mapOptions = new MapOptions();
		        mapOptions.center(new LatLong(35.347218, -117.808392))
		                .mapType(MapTypeIdEnum.SATELLITE)
		                .overviewMapControl(false)
		                .panControl(false)
		                .rotateControl(false)
		                .scaleControl(false)
		                .streetViewControl(false)
		                .zoomControl(true)
		                .zoom(17);
		        map = mapView.createMap(mapOptions);
			}
		});
	}

	private void createSensors() {
		if (DEBUG_SENSORS) {
			motor    = makePressureGauge("Motor",    "mV", 5000, 100);
			lox      = makePressureGauge("LOX",      "mV", 5000, 100);
			kerosene = makePressureGauge("Kerosene", "mV", 5000, 100);
			helium   = makePressureGauge("Helium",   "mV", 5000, 100);
			
			List<Section> sections = new ArrayList<Section>();
			sections.add(new Section(3300, 3600));
			sections.add(new Section(3600, 5000));
			
			Gauge[] gauges = new Gauge[] { motor, lox, kerosene, helium };
			for (Gauge gauge : gauges) {
				gauge.setSections(sections);
				gauge.setSectionFill0(Color.YELLOW);
				gauge.setSectionFill1(Color.RED);
			}
		} else {
			lox      = makePressureGauge("LOX",      "PSI", Pressures.LOX_MAX_PRESSURE,      10);
			kerosene = makePressureGauge("Kerosene", "PSI", Pressures.KEROSENE_MAX_PRESSURE, 10);
			helium   = makePressureGauge("Helium",   "PSI", Pressures.HELIUM_MAX_PRESSURE,   50);
			motor    = makePressureGauge("Motor",    "PSI", Pressures.MOTOR_MAX_PRESSURE,    10);
			
			lox.setSections(new Section(450, 550), new Section(550, 610), new Section(610, Pressures.LOX_MAX_PRESSURE));
			lox.setSectionFill0(Color.GREEN);
			lox.setSectionFill1(Color.YELLOW);
			lox.setSectionFill2(Color.RED);
			
			kerosene.setSections(new Section(425, 500), new Section(500, 610), new Section(610, Pressures.KEROSENE_MAX_PRESSURE));
			kerosene.setSectionFill0(Color.GREEN);
			kerosene.setSectionFill1(Color.YELLOW);
			kerosene.setSectionFill2(Color.RED);
		}
		
		accelerometerX = new NumberAxis();
		NumberAxis accelerometerY = new NumberAxis();
		accelerometerX.setAutoRanging(false);
		accelerometerX.setTickLabelsVisible(false);
		accelerometerY.setLabel("Acceleration (m/s^2)");
		accelerometerY.setForceZeroInRange(true);
		LineChart<Number, Number> accelerometer = makeChart("Accelerometer", accelerometerX, accelerometerY);
		accelerometerXData.setName("X");
		accelerometerYData.setName("Y");
		accelerometerZData.setName("Z");
		accelerometer.getData().add(accelerometerXData);
		accelerometer.getData().add(accelerometerYData);
		accelerometer.getData().add(accelerometerZData);
		
		gyroscopeX = new NumberAxis();
		NumberAxis gyroscopeY = new NumberAxis();
		gyroscopeX.setAutoRanging(false);
		gyroscopeX.setTickLabelsVisible(false);
		gyroscopeY.setLabel("Rotation (deg/sec)");
		gyroscopeY.setForceZeroInRange(true);
		LineChart<Number, Number> gyroscope = makeChart("Gyroscope", gyroscopeX, gyroscopeY);
		gyroscopeXData.setName("X");
		gyroscopeYData.setName("Y");
		gyroscopeZData.setName("Z");
		gyroscope.getData().add(gyroscopeXData);
		gyroscope.getData().add(gyroscopeYData);
		gyroscope.getData().add(gyroscopeZData);
		
		magnetometerX = new NumberAxis();
		NumberAxis magnetometerY = new NumberAxis();
		magnetometerX.setAutoRanging(false);
		magnetometerX.setTickLabelsVisible(false);
		magnetometerY.setLabel("Field Strength (Ga)");
		magnetometerY.setForceZeroInRange(true);
		LineChart<Number, Number> magnetometer = makeChart("Magnetometer", magnetometerX, magnetometerY);
		magnetometerXData.setName("X");
		magnetometerYData.setName("Y");
		magnetometerZData.setName("Z");
		magnetometer.getData().add(magnetometerXData);
		magnetometer.getData().add(magnetometerYData);
		magnetometer.getData().add(magnetometerZData);
		
		barometerX = new NumberAxis();
		NumberAxis barometerY = new NumberAxis();
		barometerX.setAutoRanging(false);
		barometerX.setTickLabelsVisible(false);
		barometerY.setLabel("Pressure (mbar)");
		barometerY.setForceZeroInRange(false);
		LineChart<Number, Number> barometer = makeChart("Barometer", barometerX, barometerY);
		barometerPressureData.setName("Pressure");
		barometer.getData().add(barometerPressureData);
		
		gaugePane.getChildren().add(lox);
		gaugePane.getChildren().add(kerosene);
		gaugePane.getChildren().add(helium);
		gaugePane.getChildren().add(motor);
		gaugePane.getChildren().add(accelerometer);
		gaugePane.getChildren().add(gyroscope);
		gaugePane.getChildren().add(magnetometer);
		gaugePane.getChildren().add(barometer);
	}

	private LineChart<Number, Number> makeChart(String title, NumberAxis x, NumberAxis y) {
		LineChart<Number, Number> chart = new LineChart<Number, Number>(x, y);
		chart.setTitle(title);
		chart.setCreateSymbols(false);
		chart.setAnimated(false);
		chart.setHorizontalZeroLineVisible(true);
		chart.setLegendSide(Side.RIGHT);
		chart.setPrefWidth(CHART_WIDTH);
		chart.setPrefHeight(CHART_HEIGHT);
		return chart;
	}

	private Gauge makePressureGauge(String label, String unit, double maxValue, double minorTickSpace) {
		return GaugeBuilder.create()
				.prefWidth(GAUGE_WIDTH).prefHeight(GAUGE_HEIGHT)
				.styleClass("gauge")
				.title(label)
				.unit(unit)
				.minValue(0)
				.maxValue(maxValue)
				.majorTickSpace(minorTickSpace * 10)
				.minorTickSpace(minorTickSpace)
				.minMeasuredValueVisible(true)
				.maxMeasuredValueVisible(true)
				.animated(false)
				.decimals(1)
				.build();
	}
	
	protected void updateLatency(float latency) {
		latencyLabel.setText("" + latency + " ms");
	}
	
	@FXML
	public void clearSensors() {
		SensorClient.Mode mode;
		Toggle selected = sensorsGroup.getSelectedToggle();
		if (remoteButton.equals(selected)) {
			mode = Mode.REMOTE;
		} else {
			mode = Mode.LOCAL;
		}
		client.setMode(mode);
		
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				motor.setValue(0);
				lox.setValue(0);
				kerosene.setValue(0);
				helium.setValue(0);
				accelerometerXData.getData().clear();
				accelerometerYData.getData().clear();
				accelerometerZData.getData().clear();
				gyroscopeXData.getData().clear();
				gyroscopeYData.getData().clear();
				gyroscopeZData.getData().clear();
				magnetometerXData.getData().clear();
				magnetometerYData.getData().clear();
				magnetometerZData.getData().clear();
				barometerPressureData.getData().clear();
			}
		});
	}
	
	public void updateSensors(Sensors sensors) {
		if (DEBUG_SENSORS) {
			motor.setValue(sensors.analog.getA0());
			lox.setValue(sensors.analog.getA1());
			kerosene.setValue(sensors.analog.getA2());
			helium.setValue(sensors.analog.getA3());
		} else {
			motor.setValue(sensors.pressures.getMotor());
			lox.setValue(sensors.pressures.getLOX());
			kerosene.setValue(sensors.pressures.getKerosene());
			helium.setValue(sensors.pressures.getHelium());
		}
		
		chartIndex++;
		
		Vector3 accelerometer = tmpVec;
		sensors.accelerometer.get(accelerometer);
		accelerometer.scl(9.8f);
		accelerometerX.setLowerBound(chartIndex - ACCELEROMETER_DATA_POINTS + 1);
		accelerometerX.setUpperBound(chartIndex);
		while (accelerometerXData.getData().size() >= ACCELEROMETER_DATA_POINTS) {
			accelerometerXData.getData().remove(0);
		}
		while (accelerometerYData.getData().size() >= ACCELEROMETER_DATA_POINTS) {
			accelerometerYData.getData().remove(0);
		}
		while (accelerometerZData.getData().size() >= ACCELEROMETER_DATA_POINTS) {
			accelerometerZData.getData().remove(0);
		}
		accelerometerXData.getData().add(new XYChart.Data<Number, Number>(chartIndex, accelerometer.x));
		accelerometerYData.getData().add(new XYChart.Data<Number, Number>(chartIndex, accelerometer.y));
		accelerometerZData.getData().add(new XYChart.Data<Number, Number>(chartIndex, accelerometer.z));
		
		Vector3 gyroscope = tmpVec;
		sensors.gyroscope.get(gyroscope);
		gyroscopeX.setLowerBound(chartIndex - GYROSCOPE_DATA_POINTS + 1);
		gyroscopeX.setUpperBound(chartIndex);
		while (gyroscopeXData.getData().size() >= GYROSCOPE_DATA_POINTS) {
			gyroscopeXData.getData().remove(0);
		}
		while (gyroscopeYData.getData().size() >= GYROSCOPE_DATA_POINTS) {
			gyroscopeYData.getData().remove(0);
		}
		while (gyroscopeZData.getData().size() >= GYROSCOPE_DATA_POINTS) {
			gyroscopeZData.getData().remove(0);
		}
		gyroscopeXData.getData().add(new XYChart.Data<Number, Number>(chartIndex, gyroscope.x));
		gyroscopeYData.getData().add(new XYChart.Data<Number, Number>(chartIndex, gyroscope.y));
		gyroscopeZData.getData().add(new XYChart.Data<Number, Number>(chartIndex, gyroscope.z));
		
		Vector3 magnetometer = tmpVec;
		sensors.magnetometer.get(magnetometer);
		magnetometerX.setLowerBound(chartIndex - MAGNETOMETER_DATA_POINTS + 1);
		magnetometerX.setUpperBound(chartIndex);
		while (magnetometerXData.getData().size() >= MAGNETOMETER_DATA_POINTS) {
			magnetometerXData.getData().remove(0);
		}
		while (magnetometerYData.getData().size() >= MAGNETOMETER_DATA_POINTS) {
			magnetometerYData.getData().remove(0);
		}
		while (magnetometerZData.getData().size() >= MAGNETOMETER_DATA_POINTS) {
			magnetometerZData.getData().remove(0);
		}
		magnetometerXData.getData().add(new XYChart.Data<Number, Number>(chartIndex, magnetometer.x));
		magnetometerYData.getData().add(new XYChart.Data<Number, Number>(chartIndex, magnetometer.y));
		magnetometerZData.getData().add(new XYChart.Data<Number, Number>(chartIndex, magnetometer.z));
		
		float pressure = sensors.barometer.getPressure();
		barometerX.setLowerBound(chartIndex - BAROMETER_DATA_POINTS + 1);
		barometerX.setUpperBound(chartIndex);
		while (barometerPressureData.getData().size() >= BAROMETER_DATA_POINTS) {
			barometerPressureData.getData().remove(0);
		}
		barometerPressureData.getData().add(new XYChart.Data<Number, Number>(chartIndex, pressure));
		
		signalLabel.setText("-" + sensors.radio.getSignalStrength());
		
		switch (sensors.gps.getFixStatus()) {
		case 1: // no fix
			gpsFixLabel.setText("No Fix");
			break;
		case 2: // 2D
			gpsFixLabel.setText("2D");
			break;
		case 3: // 3D
			gpsFixLabel.setText("3D");
			break;
		}
		gpsSatellitesLabel.setText(String.valueOf(sensors.gps.getSatellites()));
	}
	
	private void updateGPS() {
		if (System.currentTimeMillis() - lastGpsUpdateMillis > GPS_UPATE_INTERVAL_MILLIS) {
			lastGpsUpdateMillis = System.currentTimeMillis();
			
			if (marker != null) {
				map.removeMarker(marker);
				marker = null;
			}
			
			if (remote.gps.getLatitude() != 0 && remote.gps.getLongitude() != 0) {
				MarkerOptions options = new MarkerOptions();
				options.title("Altitude: "+remote.gps.getAltitude() + " m");
				options.position(new LatLong(remote.gps.getLatitude(), remote.gps.getLongitude()));
				marker = new Marker(options);
				map.addMarker(marker);
			}
			
//			if (local.gps.getLatitude() != 0 && local.gps.getLongitude() != 0) {
//				LatLong position = new LatLong(local.gps.getLatitude(), local.gps.getLongitude());
//				map.setCenter(position);
//			}
		}
	}

	@FXML
	private void onConnect(ActionEvent event) {
		if (CONNECT.equals(connectButton.getText())) {
			try {
				InetAddress addr = InetAddress.getByName(hostTextField.getText());
				client.setFrequency((float) frequencySlider.getValue());
				client.start(addr, PORT);
				
				pingThread = new Thread(new Runnable() {
					@Override
					public void run() {
						while (!Thread.currentThread().isInterrupted()) {
							try {
								Thread.sleep(1000L);
							} catch (InterruptedException e) {
								System.err.println(e);
								return;
							}
							
							try {
								client.sendPingRequest();
							} catch (IOException e) {
								System.err.println(e);
							}
						}
					}
				});
				pingThread.start();
				
				connectButton.setText(DISCONNECT);
			} catch (IOException e) {
				e.printStackTrace();
				Dialogs.create()
					.title("Connect")
					.masthead("Failed to connect.")
					.message(e.toString())
					.showException(e);
			}
		} else {
			pingThread.interrupt();
			try {
				pingThread.join();
			} catch (InterruptedException e) {
				System.err.println(e);
			}
			pingThread = null;
			
			client.stop();
			
			latencyLabel.setText("?");
			connectButton.setText(CONNECT);
		}
		
		event.consume();
	}

	/**
	 * Determines if a shutdown (quit) process should commence.
	 * 
	 * @return
	 */
	public boolean requestQuit() {
		Action response = Dialogs.create()
			.title("Quit")
			.masthead("Are you sure you want to quit?")
			.actions(Dialog.ACTION_CANCEL, Dialog.ACTION_YES)
			.showConfirm();
		return response == Dialog.ACTION_YES;
	}

}
