package edu.sdsu.rocket.core.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import edu.sdsu.rocket.core.helpers.Stopwatch;

public class HMC5883LOutputStream extends DataOutputStream {

	public static final byte SENSOR_VALUES  = 0x0;
	
	private final Stopwatch stopwatch = new Stopwatch();

	public HMC5883LOutputStream(OutputStream out) {
		super(out);
	}

	public void writeValues(short x, short y, short z) throws IOException {
		write(SENSOR_VALUES);
		writeLong(stopwatch.nanoSecondsElapsed());
		writeShort(x);
		writeShort(y);
		writeShort(z);
	}
	
}