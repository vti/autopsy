/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.swing.DefaultListSelectionModel;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.JobsSnapshot;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A dashboard for monitoring an automated ingest cluster.
 */
public final class AutoIngestDashboard extends JPanel implements Observer {

    private static final long serialVersionUID = 1L;
    private static final int GENERIC_COL_MIN_WIDTH = 30;
    private static final int GENERIC_COL_MAX_WIDTH = 2000;
    private static final int PENDING_TABLE_COL_PREFERRED_WIDTH = 280;
    private static final int RUNNING_TABLE_COL_PREFERRED_WIDTH = 175;
    private static final int STAGE_TIME_COL_MIN_WIDTH = 250;
    private static final int STAGE_TIME_COL_MAX_WIDTH = 450;
    private static final int TIME_COL_MIN_WIDTH = 30;
    private static final int TIME_COL_MAX_WIDTH = 250;
    private static final int TIME_COL_PREFERRED_WIDTH = 140;
    private static final int NAME_COL_MIN_WIDTH = 100;
    private static final int NAME_COL_MAX_WIDTH = 250;
    private static final int NAME_COL_PREFERRED_WIDTH = 140;
    private static final int STAGE_COL_MIN_WIDTH = 70;
    private static final int STAGE_COL_MAX_WIDTH = 2000;
    private static final int STAGE_COL_PREFERRED_WIDTH = 300;
    private static final int STATUS_COL_MIN_WIDTH = 55;
    private static final int STATUS_COL_MAX_WIDTH = 250;
    private static final int STATUS_COL_PREFERRED_WIDTH = 55;
    private static final int COMPLETED_TIME_COL_MIN_WIDTH = 30;
    private static final int COMPLETED_TIME_COL_MAX_WIDTH = 2000;
    private static final int COMPLETED_TIME_COL_PREFERRED_WIDTH = 280;
    private static final String UPDATE_TASKS_THREAD_NAME = "AID-update-tasks-%d";
    private static final Logger logger = Logger.getLogger(AutoIngestDashboard.class.getName());
    private final DefaultTableModel pendingTableModel;
    private final DefaultTableModel runningTableModel;
    private final DefaultTableModel completedTableModel;
    private AutoIngestMonitor autoIngestMonitor;
    private ExecutorService updateExecutor;

    /**
     * Creates a dashboard for monitoring an automated ingest cluster.
     *
     * @return The dashboard.
     *
     * @throws AutoIngestDashboardException If there is a problem creating the
     *                                      dashboard.
     */
    public static AutoIngestDashboard createDashboard() throws AutoIngestDashboardException {
        AutoIngestDashboard dashBoard = new AutoIngestDashboard();
        try {
            dashBoard.startUp();
        } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
            throw new AutoIngestDashboardException("Error starting up auto ingest dashboard", ex);
        }
        return dashBoard;
    }

    /**
     * Constructs a panel for monitoring an automated ingest cluster.
     */
    private AutoIngestDashboard() {
        pendingTableModel = new DefaultTableModel(JobsTableModelColumns.headers, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        runningTableModel = new DefaultTableModel(JobsTableModelColumns.headers, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        completedTableModel = new DefaultTableModel(JobsTableModelColumns.headers, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        initComponents();
        setServicesStatusMessage();
        initPendingJobsTable();
        initRunningJobsTable();
        initCompletedJobsTable();

        /*
         * Must set this flag, otherwise pop up menus don't close properly.
         */
        UIManager.put("PopupMenu.consumeEventOnClose", false);
    }

    /**
     * Queries the services monitor and sets the text for the services status
     * text box.
     */
    private void setServicesStatusMessage() {
        new SwingWorker<Void, Void>() {
            String caseDatabaseServerStatus = ServicesMonitor.ServiceStatus.DOWN.toString();
            String keywordSearchServiceStatus = ServicesMonitor.ServiceStatus.DOWN.toString();
            String messagingStatus = ServicesMonitor.ServiceStatus.DOWN.toString();

            @Override
            protected Void doInBackground() throws Exception {
                caseDatabaseServerStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
                keywordSearchServiceStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH);
                messagingStatus = getServiceStatus(ServicesMonitor.Service.MESSAGING);
                return null;
            }

            /**
             * Gets a status string for a given service.
             *
             * @param service The service to test.
             *
             * @return The status string.
             */
            private String getServiceStatus(ServicesMonitor.Service service) {
                String serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Unknown");
                try {
                    ServicesMonitor servicesMonitor = ServicesMonitor.getInstance();
                    serviceStatus = servicesMonitor.getServiceStatus(service.toString());
                    if (serviceStatus.compareTo(ServicesMonitor.ServiceStatus.UP.toString()) == 0) {
                        serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                    } else {
                        serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down");
                    }
                } catch (ServicesMonitor.ServicesMonitorException ex) {
                    logger.log(Level.SEVERE, String.format("Dashboard error getting service status for %s", service), ex);
                }
                return serviceStatus;
            }

            @Override
            protected void done() {
                tbServicesStatusMessage.setText(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message", caseDatabaseServerStatus, keywordSearchServiceStatus, keywordSearchServiceStatus, messagingStatus));
                String upStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                if (caseDatabaseServerStatus.compareTo(upStatus) != 0
                        || keywordSearchServiceStatus.compareTo(upStatus) != 0
                        || messagingStatus.compareTo(upStatus) != 0) {
                    tbServicesStatusMessage.setForeground(Color.RED);
                } else {
                    tbServicesStatusMessage.setForeground(Color.BLACK);
                }
            }

        }.execute();
    }

    /**
     * Sets up the JTable that presents a view of the pending jobs queue for an
     * auto ingest cluster.
     */
    private void initPendingJobsTable() {
        /*
         * Remove some of the jobs table model columns from the JTable. This
         * does not remove the columns from the model, just from this table.
         */
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STARTED_TIME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.COMPLETED_TIME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STATUS.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.JOB.getColumnHeader()));

        /*
         * Set up a column to display the cases associated with the jobs.
         */
        TableColumn column;
        column = pendingTable.getColumn(JobsTableModelColumns.CASE.getColumnHeader());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the data sources associated with the jobs.
         */
        column = pendingTable.getColumn(JobsTableModelColumns.DATA_SOURCE.getColumnHeader());
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the create times of the jobs.
         */
        column = pendingTable.getColumn(JobsTableModelColumns.CREATED_TIME.getColumnHeader());
        column.setCellRenderer(new LongDateCellRenderer());
        column.setMinWidth(TIME_COL_MIN_WIDTH);
        column.setMaxWidth(TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        column.setWidth(TIME_COL_PREFERRED_WIDTH);

        /**
         * Prevent sorting when a column header is clicked.
         */
        pendingTable.setAutoCreateRowSorter(false);

        /*
         * Create a row selection listener to enable/disable the Prioritize
         * button.
         */
        pendingTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = pendingTable.getSelectedRow();
            this.prioritizeButton.setEnabled(row >= 0 && row < pendingTable.getRowCount());
        });
    }

    /**
     * Sets up the JTable that presents a view of the running jobs list for an
     * auto ingest cluster.
     */
    private void initRunningJobsTable() {
        /*
         * Remove some of the jobs table model columns from the JTable. This
         * does not remove the columns from the model, just from this table.
         */
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.CREATED_TIME.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.STARTED_TIME.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.COMPLETED_TIME.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.STATUS.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.JOB.getColumnHeader()));

        /*
         * Set up a column to display the cases associated with the jobs.
         */
        TableColumn column;
        column = runningTable.getColumn(JobsTableModelColumns.CASE.getColumnHeader());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the image folders associated with the
         * jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.DATA_SOURCE.getColumnHeader());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the host names of the cluster nodes
         * processing the jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader());
        column.setMinWidth(NAME_COL_MIN_WIDTH);
        column.setMaxWidth(NAME_COL_MAX_WIDTH);
        column.setPreferredWidth(NAME_COL_PREFERRED_WIDTH);
        column.setWidth(NAME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the ingest activities associated with the
         * jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader());
        column.setMinWidth(STAGE_COL_MIN_WIDTH);
        column.setMaxWidth(STAGE_COL_MAX_WIDTH);
        column.setPreferredWidth(STAGE_COL_PREFERRED_WIDTH);
        column.setWidth(STAGE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the ingest activity times associated with
         * the jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader());
        column.setCellRenderer(new DurationCellRenderer());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(STAGE_TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(STAGE_TIME_COL_MIN_WIDTH);
        column.setWidth(STAGE_TIME_COL_MIN_WIDTH);

        /*
         * Prevent sorting when a column header is clicked.
         */
        runningTable.setAutoCreateRowSorter(false);
    }

    /**
     * Sets up the JTable that presents a view of the completed jobs list for an
     * auto ingest cluster.
     */
    private void initCompletedJobsTable() {
        /*
         * Remove some of the jobs table model columns from the JTable. This
         * does not remove the columns from the model, just from this table.
         */
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STARTED_TIME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.JOB.getColumnHeader()));

        /*
         * Set up a column to display the cases associated with the jobs.
         */
        TableColumn column;
        column = completedTable.getColumn(JobsTableModelColumns.CASE.getColumnHeader());
        column.setMinWidth(COMPLETED_TIME_COL_MIN_WIDTH);
        column.setMaxWidth(COMPLETED_TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);
        column.setWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the image folders associated with the
         * jobs.
         */
        column = completedTable.getColumn(JobsTableModelColumns.DATA_SOURCE.getColumnHeader());
        column.setMinWidth(COMPLETED_TIME_COL_MIN_WIDTH);
        column.setMaxWidth(COMPLETED_TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);
        column.setWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the create times of the jobs.
         */
        column = completedTable.getColumn(JobsTableModelColumns.CREATED_TIME.getColumnHeader());
        column.setCellRenderer(new LongDateCellRenderer());
        column.setMinWidth(TIME_COL_MIN_WIDTH);
        column.setMaxWidth(TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        column.setWidth(TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the completed times of the jobs.
         */
        column = completedTable.getColumn(JobsTableModelColumns.COMPLETED_TIME.getColumnHeader());
        column.setCellRenderer(new LongDateCellRenderer());
        column.setMinWidth(TIME_COL_MIN_WIDTH);
        column.setMaxWidth(TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        column.setWidth(TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the statuses of the jobs, with a cell
         * renderer that will choose an icon to represent the job status.
         */
        column = completedTable.getColumn(JobsTableModelColumns.STATUS.getColumnHeader());
        column.setCellRenderer(new CaseStatusIconCellRenderer());
        column.setMinWidth(STATUS_COL_MIN_WIDTH);
        column.setMaxWidth(STATUS_COL_MAX_WIDTH);
        column.setPreferredWidth(STATUS_COL_PREFERRED_WIDTH);
        column.setWidth(STATUS_COL_PREFERRED_WIDTH);

        /*
         * Prevent sorting when a column header is clicked.
         */
        completedTable.setAutoCreateRowSorter(false);
    }

    /**
     * Starts up the auto ingest monitor and adds this panel as an observer,
     * subscribes to services monitor events and starts a task to populate the
     * auto ingest job tables.
     */
    private void startUp() throws AutoIngestMonitor.AutoIngestMonitorException {
        setServicesStatusMessage();
        ServicesMonitor.getInstance().addSubscriber((PropertyChangeEvent evt) -> {
            setServicesStatusMessage();
        });
        autoIngestMonitor = new AutoIngestMonitor();
        autoIngestMonitor.addObserver(this);
        autoIngestMonitor.startUp();
        updateExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(UPDATE_TASKS_THREAD_NAME).build());
        updateExecutor.submit(new UpdateJobsSnapshotTask());
    }

    @Override
    public void update(Observable observable, Object arg) {
        /*
         * By creating a task to get the latest jobs snapshot, the possibility
         * of queuing a refresh task for the EDT with snaphot rendered stale by
         * the handling of a user prioritization action, etc. on the EDT is
         * avoided. This is why the snapshot pushed ny the auto ingest jobs
         * monitor is ignored.
         */
        updateExecutor.submit(new UpdateJobsSnapshotTask());
    }

    /**
     * Reloads the table models using a jobs snapshot and refreshes the JTables
     * that use the models.
     *
     * @param jobsSnapshot The jobs snapshot.
     */
    private void refreshTables(JobsSnapshot jobsSnapshot) {
        List<AutoIngestJob> pendingJobs = jobsSnapshot.getPendingJobs();
        List<AutoIngestJob> runningJobs = jobsSnapshot.getRunningJobs();
        List<AutoIngestJob> completedJobs = jobsSnapshot.getCompletedJobs();
        pendingJobs.sort(new AutoIngestJob.PriorityComparator());
        completedJobs.sort(new AutoIngestJob.ReverseCompletedDateComparator());
        refreshTable(pendingJobs, pendingTable, pendingTableModel);
        refreshTable(runningJobs, runningTable, runningTableModel);
        refreshTable(completedJobs, completedTable, completedTableModel);
    }

    /**
     * Reloads the table model for an auto ingest jobs table and refreshes the
     * JTable that uses the model.
     *
     * @param jobs       The list of auto ingest jobs.
     * @param tableModel The table model.
     * @param comparator An optional comparator (may be null) for sorting the
     *                   table model.
     */
    private void refreshTable(List<AutoIngestJob> jobs, JTable table, DefaultTableModel tableModel) {
        try {
            Path currentRow = getSelectedEntry(table, tableModel);
            tableModel.setRowCount(0);
            for (AutoIngestJob job : jobs) {
                AutoIngestJob.StageDetails status = job.getProcessingStageDetails();
                tableModel.addRow(new Object[]{
                    job.getManifest().getCaseName(), // CASE
                    job.getManifest().getDataSourcePath().getFileName(), job.getProcessingHostName(), // HOST_NAME
                    job.getManifest().getDateFileCreated(), // CREATED_TIME
                    job.getProcessingStageStartDate(), // STARTED_TIME 
                    job.getCompletedDate(), // COMPLETED_TIME
                    status.getDescription(), // STAGE
                    job.getErrorsOccurred(), // STATUS 
                    ((Date.from(Instant.now()).getTime()) - (status.getStartDate().getTime())), // STAGE_TIME
                    job.getCaseDirectoryPath(), // CASE_DIRECTORY_PATH
                    job.getManifest().getFilePath(), // MANIFEST_FILE_PATH
                    job
                });
            }
            setSelectedEntry(table, tableModel, currentRow);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error refreshing table " + table.toString(), ex);
        }
    }

    /**
     * Gets a path representing the current selection in a table.
     *
     * @param table      The table.
     * @param tableModel The table model of the table.
     *
     * @return A path representing the current selection, or null if there is no
     *         selection.
     */
    Path getSelectedEntry(JTable table, DefaultTableModel tableModel) {
        try {
            int currentlySelectedRow = table.getSelectedRow();
            if (currentlySelectedRow >= 0 && currentlySelectedRow < table.getRowCount()) {
                return Paths.get(tableModel.getValueAt(currentlySelectedRow, JobsTableModelColumns.CASE.ordinal()).toString(),
                        tableModel.getValueAt(currentlySelectedRow, JobsTableModelColumns.DATA_SOURCE.ordinal()).toString());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * Sets the selection of the table to the passed-in path's item, if that
     * item exists in the table. If it does not, clears the table selection.
     *
     * @param table      The table.
     * @param tableModel The table model of the table.
     * @param path       The path of the item to set
     */
    void setSelectedEntry(JTable table, DefaultTableModel tableModel, Path path) {
        if (path != null) {
            try {
                for (int row = 0; row < table.getRowCount(); ++row) {
                    Path temp = Paths.get(tableModel.getValueAt(row, JobsTableModelColumns.CASE.ordinal()).toString(),
                            tableModel.getValueAt(row, JobsTableModelColumns.DATA_SOURCE.ordinal()).toString());
                    if (temp.compareTo(path) == 0) { // found it
                        table.setRowSelectionInterval(row, row);
                        return;
                    }
                }
            } catch (Exception ignored) {
                table.clearSelection();
            }
        }
        table.clearSelection();
    }

    /*
     * The enum is used in conjunction with the DefaultTableModel class to
     * provide table models for the JTables used to display a view of the
     * pending jobs queue, running jobs list, and completed jobs list for an
     * auto ingest cluster. The enum allows the columns of the table model to be
     * described by either an enum ordinal or a column header string.
     */
    private enum JobsTableModelColumns {

        CASE(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Case")),
        DATA_SOURCE(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.ImageFolder")),
        HOST_NAME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.HostName")),
        CREATED_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.CreatedTime")),
        STARTED_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.StartedTime")),
        COMPLETED_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.CompletedTime")),
        STAGE(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Stage")),
        STAGE_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.StageTime")),
        STATUS(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Status")),
        CASE_DIRECTORY_PATH(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.CaseFolder")),
        MANIFEST_FILE_PATH(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.ManifestFilePath")),
        JOB(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Job"));

        private final String header;

        private JobsTableModelColumns(String header) {
            this.header = header;
        }

        private String getColumnHeader() {
            return header;
        }

        private static final String[] headers = {
            CASE.getColumnHeader(),
            DATA_SOURCE.getColumnHeader(),
            HOST_NAME.getColumnHeader(),
            CREATED_TIME.getColumnHeader(),
            STARTED_TIME.getColumnHeader(),
            COMPLETED_TIME.getColumnHeader(),
            STAGE.getColumnHeader(),
            STATUS.getColumnHeader(),
            STAGE_TIME.getColumnHeader(),
            CASE_DIRECTORY_PATH.getColumnHeader(),
            MANIFEST_FILE_PATH.getColumnHeader(),
            JOB.getColumnHeader()
        };
    };

    /**
     * A task that gets the latest auto ingest jobs snapshot from the autop
     * ingest monitor and queues a components refresh task for execution in the
     * EDT.
     */
    private class UpdateJobsSnapshotTask implements Runnable {

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            JobsSnapshot jobsSnapshot = AutoIngestDashboard.this.autoIngestMonitor.getJobsSnapshot();
            EventQueue.invokeLater(new RefreshComponentsTask(jobsSnapshot));
        }
    }

    /**
     * A task that refreshes the UI components on this panel to reflect a
     * snapshot of the pending, running and completed auto ingest jobs lists of
     * an auto ingest cluster.
     */
    private class RefreshComponentsTask implements Runnable {

        private final JobsSnapshot jobsSnapshot;

        /**
         * Constructs a task that refreshes the UI components on this panel to
         * reflect a snapshot of the pending, running and completed auto ingest
         * jobs lists of an auto ingest cluster.
         *
         * @param jobsSnapshot The jobs snapshot.
         */
        RefreshComponentsTask(JobsSnapshot jobsSnapshot) {
            this.jobsSnapshot = jobsSnapshot;
        }

        @Override
        public void run() {
            refreshTables(jobsSnapshot);
        }
    }

    /**
     * Exception type thrown when there is an error completing an auto ingest
     * dashboard operation.
     */
    static final class AutoIngestDashboardException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest dashboard operation.
         *
         * @param message The exception message.
         */
        private AutoIngestDashboardException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest dashboard operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestDashboardException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        pendingScrollPane = new javax.swing.JScrollPane();
        pendingTable = new javax.swing.JTable();
        runningScrollPane = new javax.swing.JScrollPane();
        runningTable = new javax.swing.JTable();
        completedScrollPane = new javax.swing.JScrollPane();
        completedTable = new javax.swing.JTable();
        lbPending = new javax.swing.JLabel();
        lbRunning = new javax.swing.JLabel();
        lbCompleted = new javax.swing.JLabel();
        refreshButton = new javax.swing.JButton();
        lbServicesStatus = new javax.swing.JLabel();
        tbServicesStatusMessage = new javax.swing.JTextField();
        prioritizeButton = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.jButton1.text")); // NOI18N

        pendingTable.setModel(pendingTableModel);
        pendingTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.pendingTable.toolTipText")); // NOI18N
        pendingTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        pendingTable.setRowHeight(20);
        pendingTable.setSelectionModel(new DefaultListSelectionModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == pendingTable.getSelectedRow()) {
                    pendingTable.clearSelection();
                } else {
                    super.setSelectionInterval(index0, index1);
                }
            }
        });
        pendingTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        pendingScrollPane.setViewportView(pendingTable);

        runningTable.setModel(runningTableModel);
        runningTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.runningTable.toolTipText")); // NOI18N
        runningTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        runningTable.setRowHeight(20);
        runningTable.setSelectionModel(new DefaultListSelectionModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == runningTable.getSelectedRow()) {
                    runningTable.clearSelection();
                } else {
                    super.setSelectionInterval(index0, index1);
                }
            }
        });
        runningTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        runningScrollPane.setViewportView(runningTable);

        completedTable.setModel(completedTableModel);
        completedTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.completedTable.toolTipText")); // NOI18N
        completedTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        completedTable.setRowHeight(20);
        completedTable.setSelectionModel(new DefaultListSelectionModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == completedTable.getSelectedRow()) {
                    completedTable.clearSelection();
                } else {
                    super.setSelectionInterval(index0, index1);
                }
            }
        });
        completedTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        completedScrollPane.setViewportView(completedTable);

        lbPending.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbPending, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbPending.text")); // NOI18N

        lbRunning.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRunning, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbRunning.text")); // NOI18N

        lbCompleted.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbCompleted, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbCompleted.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.text")); // NOI18N
        refreshButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.toolTipText")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        lbServicesStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbServicesStatus, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbServicesStatus.text")); // NOI18N

        tbServicesStatusMessage.setEditable(false);
        tbServicesStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbServicesStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.text")); // NOI18N
        tbServicesStatusMessage.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(prioritizeButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.prioritizeButton.text")); // NOI18N
        prioritizeButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.prioritizeButton.toolTipText")); // NOI18N
        prioritizeButton.setEnabled(false);
        prioritizeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prioritizeButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(pendingScrollPane)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPending)
                            .addComponent(lbCompleted)
                            .addComponent(lbRunning)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lbServicesStatus)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 861, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(refreshButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(prioritizeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(runningScrollPane, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(completedScrollPane, javax.swing.GroupLayout.Alignment.LEADING))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbServicesStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbPending, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(1, 1, 1)
                .addComponent(pendingScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbRunning)
                .addGap(1, 1, 1)
                .addComponent(runningScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbCompleted)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(completedScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshButton)
                    .addComponent(prioritizeButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles a click on the Refresh button. Requests a refreshed jobs snapshot
     * from the auto ingest monitor and uses it to refresh the UI components of
     * the panel.
     *
     * @param evt The button click event.
     */
    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        JobsSnapshot jobsSnapshot = autoIngestMonitor.refreshJobsSnapshot();
        refreshTables(jobsSnapshot);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_refreshButtonActionPerformed

    @Messages({
        "AutoIngestDashboard.PrioritizeError=Failed to prioritize job \"%s\"."
    })
    private void prioritizeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prioritizeButtonActionPerformed
        if (pendingTableModel.getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            AutoIngestJob job = (AutoIngestJob) (pendingTableModel.getValueAt(pendingTable.getSelectedRow(), JobsTableModelColumns.JOB.ordinal()));
            JobsSnapshot jobsSnapshot;
            try {
                jobsSnapshot = autoIngestMonitor.prioritizeJob(job);
                refreshTables(jobsSnapshot);
            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                String errorMessage = String.format(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.PrioritizeError"), job.getManifest().getFilePath());
                logger.log(Level.SEVERE, errorMessage, ex);
                MessageNotifyUtil.Message.error(errorMessage);
            }
            setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_prioritizeButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane completedScrollPane;
    private javax.swing.JTable completedTable;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel lbCompleted;
    private javax.swing.JLabel lbPending;
    private javax.swing.JLabel lbRunning;
    private javax.swing.JLabel lbServicesStatus;
    private javax.swing.JScrollPane pendingScrollPane;
    private javax.swing.JTable pendingTable;
    private javax.swing.JButton prioritizeButton;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane runningScrollPane;
    private javax.swing.JTable runningTable;
    private javax.swing.JTextField tbServicesStatusMessage;
    // End of variables declaration//GEN-END:variables

}
