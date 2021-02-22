import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class IdWorker {
    //The starting point of time, as a reference, generally take the latest time of the system (it cannot be changed once determined)
    private final static long twepoch = 1288834974657L;
    
    // Number of machine ID
    private final static long workerIdBits = 5L;
    // Data center ID number
    private final static long datacenterIdBits = 5L;
    // Maximum machine ID
    private final static long maxWorkerId = -1L ^ (-1L << workerIdBits);
    // Maximum Data Center ID
    private final static long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    // Increment within milliseconds
    private final static long sequenceBits = 12L;
    // The machine ID is shifted to the left by 12 bits
    private final static long workerIdShift = sequenceBits;
    // Data center ID is shifted 17 bits to the left
    private final static long datacenterIdShift = sequenceBits + workerIdBits;
    // Time milliseconds shifted to the left by 22 bits
    private final static long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
 
    private final static long sequenceMask = -1L ^ (-1L << sequenceBits);
    /* Last production id timestamp */
    private static long lastTimestamp = -1L;
    // 0，Concurrency control
    private long sequence = 0L;
 
    private final long workerId;
    // Data identify for ID part
    private final long datacenterId;
 
    public IdWorker(){
        this.datacenterId = getDatacenterId(maxDatacenterId);
        this.workerId = getMaxWorkerId(datacenterId, maxWorkerId);
    }
    /**
     * @param workerId
     *            Working machine ID
     * @param datacenterId
     *            serial number
     */
    public IdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }
    /**
     * Get the next ID
     *
     * @return
     */
    public synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
 
        if (lastTimestamp == timestamp) {
            // Within the current millisecond, then+1
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // If the count is full within the current millisecond, wait for the next second
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        // ID offset combination generates the final ID, and returns the ID
        long nextId = ((timestamp - twepoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift) | sequence;
 
        return nextId;
    }
 
    private long tilNextMillis(final long lastTimestamp) {
        long timestamp = this.timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = this.timeGen();
        }
        return timestamp;
    }
 
    private long timeGen() {
        return System.currentTimeMillis();
    }
 
     //Obtain maxWorkerId
    protected static long getMaxWorkerId(long datacenterId, long maxWorkerId) {
        StringBuffer mpid = new StringBuffer();
        mpid.append(datacenterId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (!name.isEmpty()) {
            /*
             * GET jvmPid
             */
            mpid.append(name.split("@")[0]);
        }
        /*
         * MAC + PID hashcode gets 16 low bits
         */
        return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
    }
 
     // Data identify ID part
     
    protected static long getDatacenterId(long maxDatacenterId) {
        long id = 0L;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            if (network == null) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                id = ((0x000000FF & (long) mac[mac.length - 1])
                        | (0x0000FF00 & (((long) mac[mac.length - 2]) << 8))) >> 6;
                id = id % (maxDatacenterId + 1);
            }
        } catch (Exception e) {
            System.out.println(" getDatacenterId: " + e.getMessage());
        }
        return id;
    }

}
