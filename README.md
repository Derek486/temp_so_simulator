# Operating Systems Simulator - Java Edition

A comprehensive educational simulator integrating CPU scheduling and virtual memory management with GUI visualization.

## Project Structure

```
OSSimulator_Java/
├── src/
│   └── com/ossimulator/
│       ├── Main.java
│       ├── gui/
│       │   ├── MainWindow.java
│       │   ├── SchedulingPanel.java
│       │   ├── MemoryPanel.java
│       │   ├── MetricsPanel.java
│       │   └── GanttChart.java
│       ├── scheduling/
│       │   ├── SchedulingAlgorithm.java
│       │   ├── FCFS.java
│       │   ├── SJF.java
│       │   ├── RoundRobin.java
│       │   └── PriorityScheduling.java
│       ├── memory/
│       │   ├── PageReplacementAlgorithm.java
│       │   ├── FIFO.java
│       │   ├── LRU.java
│       │   ├── Optimal.java
│       │   └── MemoryManager.java
│       ├── process/
│       │   ├── Process.java
│       │   ├── ProcessState.java
│       │   ├── Burst.java
│       │   └── BurstType.java
│       ├── simulator/
│       │   ├── OSSimulator.java
│       │   ├── SystemMetrics.java
│       │   └── EventLogger.java
│       └── util/
│           └── ConfigParser.java
├── resources/
│   └── procesos.txt
└── build.xml
```

## Features

### CPU Scheduling (3+ Algorithms)
- **FCFS** (First Come, First Served) - Non-preemptive
- **SJF** (Shortest Job First) - Non-preemptive
- **Round Robin** - Preemptive with configurable quantum
- **Priority Scheduling** - Preemptive with priority levels

### Virtual Memory (3+ Algorithms)
- **FIFO** (First In, First Out)
- **LRU** (Least Recently Used)
- **Optimal** - Lookahead page replacement

### Features
- Real-time Gantt chart visualization
- Memory frame status display
- Page table tracking
- I/O burst handling (+2 extra points)
- Performance metrics (avg wait time, avg turnaround, CPU utilization)
- Process state tracking (New, Ready, Running, Blocked, Terminated)
- Synchronization with semaphores and monitors
- Event logging and statistics
- Configuration via text file or GUI

## Getting Started

### Prerequisites
- JDK 11 or higher
- IntelliJ IDEA, Eclipse, or NetBeans

### Setup in IntelliJ IDEA

1. Open IntelliJ IDEA
2. File → Open → Select the `OSSimulator_Java` folder
3. Mark `src` as Sources Root (right-click → Mark Directory as → Sources Root)
4. Run `Main.java`

### Setup in Eclipse

1. File → Import → General → Existing Projects into Workspace
2. Select `OSSimulator_Java` folder
3. Right-click project → Build Path → Configure Build Path
4. Add JDK if needed
5. Run as Java Application

### Input Format (procesos.txt)

```
# PID ArrivalTime Bursts Priority Pages
P1 0 CPU(4),E/S(3),CPU(5) 1 4
P2 2 CPU(6),E/S(2),CPU(3) 2 5
P3 4 CPU(8) 3 6
```

## Configuration

GUI allows setting:
- Number of memory frames
- Scheduling algorithm
- Page replacement algorithm
- Round Robin quantum (if applicable)
- Simulation speed

## Performance Metrics

- Average Waiting Time
- Average Turnaround Time
- CPU Utilization %
- Total Page Faults
- Total Page Replacements
- Context Switches

## Compilation & Execution

```bash
cd OSSimulator_Java
javac -d bin src/com/ossimulator/**/*.java
java -cp bin com.ossimulator.Main
```

Or use your IDE's build and run features.

## Notes

- All modules use proper synchronization (Semaphores, ReentrantLock, Condition Variables)
- Each process runs as a separate Thread
- Memory manager coordinates with scheduler
- I/O operations handled asynchronously
- All edits visible in real-time through GUI
