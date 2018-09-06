/*
 * ========================LICENSE_START=================================
 * pgSqlBlocks
 * %
 * Copyright (C) 2017 "Technology" LLC
 * %
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package ru.taximaxim.pgsqlblocks.modules.processes.controller;

import org.apache.log4j.Logger;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import ru.taximaxim.pgsqlblocks.PgSqlBlocks;
import ru.taximaxim.pgsqlblocks.common.DBModelsProvider;
import ru.taximaxim.pgsqlblocks.common.FilterCondition;
import ru.taximaxim.pgsqlblocks.common.models.DBBlocksJournalProcess;
import ru.taximaxim.pgsqlblocks.common.models.DBModel;
import ru.taximaxim.pgsqlblocks.common.models.DBProcess;
import ru.taximaxim.pgsqlblocks.common.models.DBProcessFilter;
import ru.taximaxim.pgsqlblocks.common.ui.*;
import ru.taximaxim.pgsqlblocks.dialogs.*;
import ru.taximaxim.pgsqlblocks.modules.blocksjournal.view.BlocksJournalView;
import ru.taximaxim.pgsqlblocks.modules.db.controller.DBController;
import ru.taximaxim.pgsqlblocks.modules.db.controller.DBControllerListener;
import ru.taximaxim.pgsqlblocks.modules.db.model.DBStatus;
import ru.taximaxim.pgsqlblocks.modules.processes.view.ProcessesView;
import ru.taximaxim.pgsqlblocks.utils.*;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class ProcessesController implements DBControllerListener, DBModelsViewListener, SettingsListener,
        DBProcessesViewDataSourceFilterListener, DBProcessesFiltersViewListener,
        TMTreeViewerSortColumnSelectionListener, DBProcessInfoViewListener {

    private static final Logger LOG = Logger.getLogger(ProcessesController.class);

    private final Settings settings;
    private final ResourceBundle resourceBundle;

    private ProcessesView view;

    private DBModelsView dbModelsView;
    private DBProcessesView dbProcessesView;
    private DBProcessesFiltersView dbProcessesFiltersView;
    private DBProcessInfoView dbProcessInfoView;

    private DBProcessesView dbBlocksJournalView;

    private DBProcessInfoView dbBlocksJournalProcessInfoView;
    private DBProcessesFiltersView dbBlocksJournalProcessesFiltersView;

    private final DBProcessesViewDataSourceFilter dbProcessesViewDataSourceFilter = new DBProcessesViewDataSourceFilter();

    private TabFolder tabFolder;

    private ToolItem addDatabaseToolItem;
    private ToolItem deleteDatabaseToolItem;
    private ToolItem editDatabaseToolItem;
    private ToolItem connectDatabaseToolItem;
    private ToolItem disconnectDatabaseToolItem;
    private ToolItem updateProcessesToolItem;
    private ToolItem autoUpdateToolItem;
    private ToolItem showOnlyBlockedProcessesToolItem;
    private ToolItem toggleVisibilityProcessesFilterPanelToolItem;

    private ToolItem toggleVisibilityBlocksJournalProcessesFilterPanelToolItem;

    private DBModelsProvider dbModelsProvider;

    private List<DBController> dbControllers = new ArrayList<>();

    private List<DBProcess> selectedProcesses = new ArrayList<>();

    public ProcessesController(Settings settings, DBModelsProvider dbModelsProvider) {
        this.settings = settings;
        this.resourceBundle = settings.getResourceBundle();
        this.dbModelsProvider = dbModelsProvider;
        settings.addListener(this);
    }

    public void setView(ProcessesView view) {
        this.view = view;
    }

    public void load() {
        createToolItems();

        dbModelsView = new DBModelsView(resourceBundle, view.getLeftPanelComposite(), SWT.NONE);
        dbModelsView.getTableViewer().setInput(dbControllers);
        dbModelsView.addListener(this);

        dbProcessesViewDataSourceFilter.addListener(this);

        tabFolder = new TabFolder(view.getRightPanelComposite(), SWT.NONE);

        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createProcessesTab();
        createBlocksJournalTab();

        loadDatabases();

        dbControllers.stream().filter(DBController::isEnabledAutoConnection).forEach(DBController::connect);
    }

    private void createProcessesTab() {
        TabItem processesTabItem = new TabItem(tabFolder, SWT.BORDER);
        processesTabItem.setText(l10n("current_activity"));

        Composite processesViewComposite = new Composite(tabFolder, SWT.NONE);
        GridLayout gl = new GridLayout();
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        processesViewComposite.setLayout(gl);

        ToolBar processesViewToolBar = new ToolBar(processesViewComposite, SWT.HORIZONTAL);
        processesViewToolBar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        updateProcessesToolItem = new ToolItem(processesViewToolBar, SWT.PUSH);
        updateProcessesToolItem.setImage(ImageUtils.getImage(Images.UPDATE));
        updateProcessesToolItem.setToolTipText(Images.UPDATE.getDescription(resourceBundle));
        updateProcessesToolItem.addListener(SWT.Selection, event -> updateProcessesInSelectedDatabase());

        new ToolItem(processesViewToolBar, SWT.SEPARATOR);

        toggleVisibilityProcessesFilterPanelToolItem = new ToolItem(processesViewToolBar, SWT.CHECK);
        toggleVisibilityProcessesFilterPanelToolItem.setImage(ImageUtils.getImage(Images.FILTER));
        toggleVisibilityProcessesFilterPanelToolItem.setToolTipText(Images.FILTER.getDescription(resourceBundle));
        toggleVisibilityProcessesFilterPanelToolItem.addListener(SWT.Selection, event ->
                setProcessesFilterViewVisibility(toggleVisibilityProcessesFilterPanelToolItem.getSelection()));
        toggleVisibilityProcessesFilterPanelToolItem.setEnabled(false);

        ToolItem showColumnsDialogToolItem = new ToolItem(processesViewToolBar, SWT.PUSH);
        showColumnsDialogToolItem.setImage(ImageUtils.getImage(Images.TABLE));
        showColumnsDialogToolItem.setToolTipText(Images.TABLE.getDescription(resourceBundle));
        showColumnsDialogToolItem.addListener(SWT.Selection, event -> showProcessesViewColumnsDialog());

        dbProcessesFiltersView = new DBProcessesFiltersView(resourceBundle, processesViewComposite, SWT.NONE);
        dbProcessesFiltersView.addListener(this);
        dbProcessesFiltersView.hide();

        dbProcessesView = new DBProcessesView(processesViewComposite, SWT.NONE);
        DBProcessesViewDataSource dbProcessesViewDataSource = new DBProcessesViewDataSource(resourceBundle, dbProcessesViewDataSourceFilter);
        dbProcessesView.getTreeViewer().setDataSource(dbProcessesViewDataSource);
        dbProcessesView.getTreeViewer().addSortColumnSelectionListener(this);
        dbProcessesView.getTreeViewer().addSelectionChangedListener(this::dbProcessesViewSelectionChanged);
        dbProcessesView.getTreeViewer().getTree().addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                ITreeSelection element = dbProcessesView.getTreeViewer().getStructuredSelection();
                IStructuredSelection structuredSelection = element;
                selectedProcesses = (List<DBProcess>) structuredSelection.toList();
                openProcessDialogInfo(selectedProcesses.get(0));
            }
        });

        dbProcessInfoView = new DBProcessInfoView(resourceBundle, processesViewComposite, SWT.NONE);
        dbProcessInfoView.addListener(this);
        dbProcessInfoView.hide();

        processesTabItem.setControl(processesViewComposite);
    }

    private void openProcessDialogInfo(DBProcess dbProcess){
        DBProcessInfoDialog dbProcessInfoDialog = new DBProcessInfoDialog(resourceBundle, view.getShell(), dbProcess,
                new DBProcessInfoDialog.ProcessInfoListener() {
                    @Override
                    public void terminateButtonClick() {
                        dbProcessInfoViewTerminateProcessToolItemClicked();
                        close();
                    }

                    @Override
                    public void cancelButtonClick() {
                        dbProcessInfoViewCancelProcessToolItemClicked();
                        close();
                    }
                });
        dbProcessInfoDialog.open();
    }

    private void createBlocksJournalTab() {
        TabItem blocksJournalTabItem = new TabItem(tabFolder, SWT.NONE);
        blocksJournalTabItem.setText(l10n("blocks_journal"));

        Composite dbBlocksJournalViewComposite = new Composite(tabFolder, SWT.NONE);
        GridLayout gl = new GridLayout();
        gl.marginWidth= 0;
        gl.marginHeight = 0;

        dbBlocksJournalViewComposite.setLayout(gl);

        ToolBar dbBlocksJournalViewToolBar = new ToolBar(dbBlocksJournalViewComposite, SWT.HORIZONTAL);
        dbBlocksJournalViewToolBar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem = new ToolItem(dbBlocksJournalViewToolBar, SWT.CHECK);
        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.setImage(ImageUtils.getImage(Images.FILTER));
        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.setToolTipText(Images.FILTER.getDescription(resourceBundle));
        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.addListener(SWT.Selection, event ->
                setBlocksJournalProcessesFilterViewVisibility(toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.getSelection()));
        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.setEnabled(false);

        ToolItem showColumnsDialogToolItem = new ToolItem(dbBlocksJournalViewToolBar, SWT.PUSH);
        showColumnsDialogToolItem.setImage(ImageUtils.getImage(Images.TABLE));
        showColumnsDialogToolItem.setToolTipText(l10n("columns"));
        showColumnsDialogToolItem.addListener(SWT.Selection, event -> showDbBlocksJournalViewColumnsDialog());

        dbBlocksJournalProcessesFiltersView = new DBProcessesFiltersView(resourceBundle, dbBlocksJournalViewComposite, SWT.NONE);
        dbBlocksJournalProcessesFiltersView.addListener(this);
        dbBlocksJournalProcessesFiltersView.hide();

        dbBlocksJournalView = new DBProcessesView(dbBlocksJournalViewComposite, SWT.NONE);
        dbBlocksJournalView.getTreeViewer().setDataSource(new DBBlocksJournalViewDataSource(resourceBundle));
        dbBlocksJournalView.getTreeViewer().addSelectionChangedListener(this::dbBlocksJournalViewSelectionChanged);
        dbBlocksJournalView.getTreeViewer().getTree().addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                IStructuredSelection structuredSelection = dbProcessesView.getTreeViewer().getStructuredSelection();
                selectedProcesses = (List<DBProcess>) structuredSelection.toList();
                openProcessDialogInfo(selectedProcesses.get(0));
            }
        });

        dbBlocksJournalProcessInfoView = new DBProcessInfoView(resourceBundle, dbBlocksJournalViewComposite, SWT.NONE);
        dbBlocksJournalProcessInfoView.hideToolBar();
        dbBlocksJournalProcessInfoView.hide();

        blocksJournalTabItem.setControl(dbBlocksJournalViewComposite);
    }

    private void createToolItems() {
        addDatabaseToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        addDatabaseToolItem.setImage(ImageUtils.getImage(Images.ADD_DATABASE));
        addDatabaseToolItem.setToolTipText(Images.ADD_DATABASE.getDescription(resourceBundle));
        addDatabaseToolItem.addListener(SWT.Selection, event -> openAddNewDatabaseDialog());

        deleteDatabaseToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        deleteDatabaseToolItem.setImage(ImageUtils.getImage(Images.DELETE_DATABASE));
        deleteDatabaseToolItem.setToolTipText(Images.DELETE_DATABASE.getDescription(resourceBundle));
        deleteDatabaseToolItem.setEnabled(false);
        deleteDatabaseToolItem.addListener(SWT.Selection, event -> deleteSelectedDatabase());

        editDatabaseToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        editDatabaseToolItem.setImage(ImageUtils.getImage(Images.EDIT_DATABASE));
        editDatabaseToolItem.setToolTipText(Images.EDIT_DATABASE.getDescription(resourceBundle));
        editDatabaseToolItem.setEnabled(false);
        editDatabaseToolItem.addListener(SWT.Selection, event -> openEditSelectedDatabaseDialog());

        connectDatabaseToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        connectDatabaseToolItem.setImage(ImageUtils.getImage(Images.CONNECT_DATABASE));
        connectDatabaseToolItem.setToolTipText(Images.CONNECT_DATABASE.getDescription(resourceBundle));
        connectDatabaseToolItem.setEnabled(false);
        connectDatabaseToolItem.addListener(SWT.Selection, event -> connectToSelectedDatabase());

        disconnectDatabaseToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        disconnectDatabaseToolItem.setImage(ImageUtils.getImage(Images.DISCONNECT_DATABASE));
        disconnectDatabaseToolItem.setToolTipText(Images.DISCONNECT_DATABASE.getDescription(resourceBundle));
        disconnectDatabaseToolItem.setEnabled(false);
        disconnectDatabaseToolItem.addListener(SWT.Selection, event -> disconnectSelectedDatabase());

        new ToolItem(view.getToolBar(), SWT.SEPARATOR);

        ToolItem showSettingsDialogToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        showSettingsDialogToolItem.setImage(ImageUtils.getImage(Images.SETTINGS));
        showSettingsDialogToolItem.setToolTipText(Images.SETTINGS.getDescription(resourceBundle));
        showSettingsDialogToolItem.addListener(SWT.Selection, event -> showSettingsDialog());

        ToolItem toggleLogsPanelVisibilityToolItem = new ToolItem(view.getToolBar(), SWT.CHECK);
        toggleLogsPanelVisibilityToolItem.setImage(ImageUtils.getImage(Images.SHOW_LOG_PANEL));
        toggleLogsPanelVisibilityToolItem.setToolTipText(Images.SHOW_LOG_PANEL.getDescription(resourceBundle));
        toggleLogsPanelVisibilityToolItem.setSelection(true);
        toggleLogsPanelVisibilityToolItem.addListener(SWT.Selection, event -> toggleLogsPanelVisibility(toggleLogsPanelVisibilityToolItem));

        new ToolItem(view.getToolBar(), SWT.SEPARATOR);

        autoUpdateToolItem = new ToolItem(view.getToolBar(), SWT.CHECK);
        autoUpdateToolItem.setImage(ImageUtils.getImage(Images.AUTOUPDATE));
        autoUpdateToolItem.setToolTipText(Images.AUTOUPDATE.getDescription(resourceBundle));
        autoUpdateToolItem.addListener(SWT.Selection, event -> setAutoUpdate(autoUpdateToolItem.getSelection()));
        autoUpdateToolItem.setSelection(settings.isAutoUpdate());

        showOnlyBlockedProcessesToolItem = new ToolItem(view.getToolBar(), SWT.CHECK);
        showOnlyBlockedProcessesToolItem.setImage(ImageUtils.getImage(Images.VIEW_ONLY_BLOCKED));
        showOnlyBlockedProcessesToolItem.setToolTipText(Images.VIEW_ONLY_BLOCKED.getDescription(resourceBundle));
        showOnlyBlockedProcessesToolItem.addListener(SWT.Selection, event ->
                dbProcessesViewDataSourceFilter.setShowOnlyBlockedProcesses(showOnlyBlockedProcessesToolItem.getSelection()));

        new ToolItem(view.getToolBar(), SWT.SEPARATOR);

        ToolItem openBlocksJournalToolItem = new ToolItem(view.getToolBar(), SWT.PUSH);
        openBlocksJournalToolItem.setImage(ImageUtils.getImage(Images.BLOCKS_JOURNAL_FOLDER));
        openBlocksJournalToolItem.setToolTipText(Images.BLOCKS_JOURNAL_FOLDER.getDescription(resourceBundle));
        openBlocksJournalToolItem.addListener(SWT.Selection, event -> {
            BlocksJournalView blocksJournalView = new BlocksJournalView(settings);
            blocksJournalView.open();
        });

    }

    private void changeToolItemsStateForController(DBController controller) {
        Display.getDefault().syncExec( () -> {
            boolean isDisconnected = controller != null && !controller.isConnected();
            toggleVisibilityProcessesFilterPanelToolItem.setEnabled(controller != null);
            toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.setEnabled(controller != null);
            connectDatabaseToolItem.setEnabled(isDisconnected);
            disconnectDatabaseToolItem.setEnabled(!isDisconnected);
            deleteDatabaseToolItem.setEnabled(isDisconnected);
            editDatabaseToolItem.setEnabled(isDisconnected);
        });
    }

    private void toggleLogsPanelVisibility(ToolItem toolItem) {
        boolean isShow = toolItem.getSelection();
        if (isShow) {
            toolItem.setImage(ImageUtils.getImage(Images.SHOW_LOG_PANEL));
            toolItem.setToolTipText(Images.SHOW_LOG_PANEL.getDescription(resourceBundle));
            PgSqlBlocks.getInstance().getApplicationController().getApplicationView().showBottomPanel();
        } else {
            toolItem.setImage(ImageUtils.getImage(Images.HIDE_LOG_PANEL));
            toolItem.setToolTipText(Images.HIDE_LOG_PANEL.getDescription(resourceBundle));
            PgSqlBlocks.getInstance().getApplicationController().getApplicationView().hideBottomPanel();
        }
    }

    private void loadDatabases() {
        List<DBModel> dbModels = dbModelsProvider.get();
        dbModels.forEach(this::addDatabase);
        dbModelsView.getTableViewer().refresh();
    }

    private DBController addDatabase(DBModel dbModel) {
        return addDatabase(dbModel, dbControllers.size());
    }

    private DBController addDatabase(DBModel dbModel, int index) {
        DBController controller = new DBController(settings, dbModel);
        controller.addListener(this);
        dbControllers.add(index, controller);
        return controller;
    }

    private void deleteDatabase(DBController dbController) {
        dbController.removeListener(this);
        dbController.shutdown();
        dbControllers.remove(dbController);
        changeToolItemsStateForController(null);
        dbProcessesView.getTreeViewer().setInput(null);
        dbBlocksJournalView.getTreeViewer().setInput(null);
        toggleVisibilityProcessesFilterPanelToolItem.setSelection(false);
        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.setSelection(false);
        dbProcessesFiltersView.hide();
        dbProcessesFiltersView.fillView(null, "");
        dbBlocksJournalProcessesFiltersView.hide();
        dbBlocksJournalProcessesFiltersView.fillView(null, "");
    }

    private void saveDatabases() {
        List<DBModel> models = dbControllers.stream().map(DBController::getModel).collect(Collectors.toList());
        dbModelsProvider.save(models);
    }

    private void openAddNewDatabaseDialog() {
        List<String> reservedConnectionNames = dbControllers.stream()
                .map(DBController::getModel)
                .map(DBModel::getName)
                .collect(Collectors.toList());
        AddDatabaseDialog addDatabaseDialog = new AddDatabaseDialog(resourceBundle, view.getShell(), reservedConnectionNames);
        if (addDatabaseDialog.open() == Window.OK) {
            DBController controller = addDatabase(addDatabaseDialog.getCreatedModel());
            dbModelsView.getTableViewer().refresh();
            if (controller.isEnabledAutoConnection()) {
                controller.connect();
            }
            saveDatabases();
        }
    }

    private void editDatabase(DBModel oldModel, DBModel newModel) {
        Optional<DBController> opt = dbControllers.stream().filter(dbc -> dbc.getModel().equals(oldModel)).findFirst();
        opt.ifPresent(controller -> {
            controller.setModel(newModel);
            dbModelsView.getTableViewer().refresh(true, true);
            saveDatabases();
            if (controller.isEnabledAutoConnection()) {
                controller.connect();
            }
        });
    }

    private void openEditSelectedDatabaseDialog() {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() == null) {
            return;
        }
        DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        List<String> reservedConnectionNames = dbControllers.stream()
                .map(DBController::getModel)
                .map(DBModel::getName)
                .collect(Collectors.toList());
        EditDatabaseDialog editDatabaseDialog =
                new EditDatabaseDialog(resourceBundle, view.getShell(), reservedConnectionNames, selectedController.getModel());
        if (editDatabaseDialog.open() == Window.OK) {
            editDatabase(editDatabaseDialog.getEditedModel(), editDatabaseDialog.getCreatedModel());
        }
    }

    private void deleteSelectedDatabase() {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() == null) {
            return;
        }
        DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        deleteDatabase(selectedController);
        dbModelsView.getTableViewer().refresh();
        saveDatabases();
    }

    private void connectToSelectedDatabase() {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() != null) {
            DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
            selectedController.connect();
        }
    }

    private void disconnectSelectedDatabase() {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() != null) {
            DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
            selectedController.disconnect(true);
        }
    }

    private void updateProcessesInSelectedDatabase() {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() != null) {
            DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
            selectedController.updateProcesses();
        }
    }

    private void setAutoUpdate(boolean autoUpdate) {
        settings.setAutoUpdate(autoUpdate);
    }

    private void setProcessesFilterViewVisibility(boolean isVisible) {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() == null) {
            return;
        }
        DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        selectedController.getProcessesFilters().setEnabled(isVisible);
        if (isVisible) {
            dbProcessesFiltersView.show();
        } else {
            dbProcessesFiltersView.hide();
        }
    }

    private void setBlocksJournalProcessesFilterViewVisibility(boolean isVisible) {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() == null) {
            return;
        }
        DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        selectedController.getBlocksJournal().getProcessesFilters().setEnabled(isVisible);
        if (isVisible) {
            dbBlocksJournalProcessesFiltersView.show();
        } else {
            dbBlocksJournalProcessesFiltersView.hide();
        }
    }

    private void showDBBlockedMessageInTray(DBController controller) {
        view.getDisplay().asyncExec(() -> {
            if (PgSqlBlocks.getInstance().getApplicationController().getApplicationView().trayIsAvailable()) {
                Tray tray = PgSqlBlocks.getInstance().getApplicationController().getApplicationView().getTray();
                TrayItem trayItem = tray.getItem(0);
                trayItem.setImage(ImageUtils.getImage(Images.BLOCKED));
                ToolTip toolTip = trayItem.getToolTip();
                toolTip.setMessage(l10n("db_has_lock", controller.getModel().getName()));
                toolTip.setVisible(true);
            } else {
                LOG.error("Tray is not available");
            }
        });
    }

    private void hideTrayMessageIfAllDatabasesUnblocked() {
        if (PgSqlBlocks.getInstance().getApplicationController().getApplicationView().trayIsAvailable() 
                && dbControllers.stream().noneMatch(DBController::isBlocked)) {
            view.getDisplay().asyncExec(() -> {
                Tray tray = PgSqlBlocks.getInstance().getApplicationController().getApplicationView().getTray();
                TrayItem trayItem = tray.getItem(0);
                trayItem.setImage(ImageUtils.getImage(Images.UNBLOCKED));
                ToolTip toolTip = trayItem.getToolTip();
                toolTip.setVisible(false);
            });
        }
    }

    private void showSettingsDialog() {
        SettingsDialog settingsDialog = new SettingsDialog(settings, view.getShell());
        settingsDialog.open();
    }

    private void showProcessesViewColumnsDialog() {
        TMTreeViewerColumnsDialog dialog =
                new TMTreeViewerColumnsDialog(resourceBundle, dbProcessesView.getTreeViewer(), view.getShell());
        dialog.open();
    }

    private void showDbBlocksJournalViewColumnsDialog() {
        TMTreeViewerColumnsDialog dialog =
                new TMTreeViewerColumnsDialog(resourceBundle, dbBlocksJournalView.getTreeViewer(), view.getShell());
        dialog.open();
    }

    public void close() {
        dbControllers.forEach(DBController::shutdown);
        settings.removeListener(this);
    }

    @Override
    public void dbControllerStatusChanged(DBController controller, DBStatus newStatus) {
        view.getDisplay().asyncExec(() -> dbModelsView.getTableViewer().refresh(controller, true, true));
    }

    @Override
    public void dbControllerDidConnect(DBController controller) {
        if (settings.isAutoUpdate()) {
            controller.startProcessesUpdater();
        } else {
            controller.updateProcesses();
        }
        changeToolItemsStateForController(controller);
    }

    @Override
    public void dbControllerWillConnect(DBController controller) {
        LOG.info(l10n("db_connecting", controller.getModel().getName()));
    }

    @Override
    public void dbControllerConnectionFailed(DBController controller, SQLException exception) {
        LOG.error(controller.getModel().getName() + " " + exception.getMessage(), exception);
    }

    @Override
    public void dbControllerDisconnectFailed(DBController controller, boolean forcedByUser, SQLException exception) {
        if (!forcedByUser && settings.isAutoUpdate()) {
            LOG.info(l10n("db_disconnected_will_reconnect",
                    controller.getModel().getName(), settings.getUpdatePeriod()));
            controller.startProcessesUpdater(settings.getUpdatePeriod());
        } else {
            LOG.error(controller.getModel().getName() + " " + exception.getMessage(), exception);
        }
        changeToolItemsStateForController(controller);
    }

    @Override
    public void dbControllerDidDisconnect(DBController controller, boolean forcedByUser) {
        if (!forcedByUser && settings.isAutoUpdate()) {
            LOG.info(l10n("db_disconnected_will_reconnect",
                    controller.getModel().getName(), settings.getUpdatePeriod()));
            controller.startProcessesUpdater(settings.getUpdatePeriod());
        } else {
            LOG.info(l10n("db_disconnected", controller.getModel().getName()));
        }
        changeToolItemsStateForController(controller);
    }

    @Override
    public void dbControllerWillUpdateProcesses(DBController controller) {
        LOG.info(l10n("db_updating", controller.getModel().getName()));
    }

    @Override
    public void dbControllerProcessesUpdated(DBController controller) {
        view.getDisplay().asyncExec(() -> {
            dbModelsView.getTableViewer().refresh(controller);
            if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() != null) {
                DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
                if (controller.equals(selectedController)) {
                    dbProcessesView.getTreeViewer().refresh();
                }
            }
        });
    }

    @Override
    public void dbControllerBlockedChanged(DBController controller) {
        if (settings.getShowToolTip()) {
            view.getDisplay().asyncExec(() -> dbModelsView.getTableViewer().refresh(controller));
            if (controller.isBlocked()) {
                showDBBlockedMessageInTray(controller);
            } else {
                hideTrayMessageIfAllDatabasesUnblocked();
            }
        }
    }

    @Override
    public void dbControllerProcessesFilterChanged(DBController controller) {
        dbProcessesView.getTreeViewer().refresh();
    }

    @Override
    public void dbControllerBlocksJournalChanged(DBController controller) {
        view.getDisplay().asyncExec(() -> {
            if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() != null) {
                DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
                if (controller.equals(selectedController)) {
                    dbBlocksJournalView.getTreeViewer().refresh();
                }
            }
        });
    }

    @Override
    public void dbModelsViewDidSelectController(DBController controller) {
        dbProcessesView.getTreeViewer().setInput(controller.getFilteredProcesses());
        dbBlocksJournalView.getTreeViewer().setInput(controller.getBlocksJournal().getFilteredProcesses());
        changeToolItemsStateForController(controller);
        dbProcessesFiltersView.fillView(controller.getProcessesFilters(), controller.getModel().getDatabaseName());
        boolean controllerFiltersEnabled = controller.getProcessesFilters().isEnabled();
        if (controllerFiltersEnabled) {
            dbProcessesFiltersView.show();
        } else {
            dbProcessesFiltersView.hide();
        }
        toggleVisibilityProcessesFilterPanelToolItem.setSelection(controllerFiltersEnabled);

        dbBlocksJournalProcessesFiltersView.fillView(controller.getBlocksJournal().getProcessesFilters(),
                controller.getModel().getDatabaseName());
        boolean controllerBlocksJournalFiltersEnabled = controller.getBlocksJournal().getProcessesFilters().isEnabled();
        if (controllerBlocksJournalFiltersEnabled) {
            dbBlocksJournalProcessesFiltersView.show();
        } else {
            dbBlocksJournalProcessesFiltersView.hide();
        }
        toggleVisibilityBlocksJournalProcessesFilterPanelToolItem.setSelection(controllerBlocksJournalFiltersEnabled);
    }

    @Override
    public void dbModelsViewDidCallActionToController(DBController controller) {
        if (!controller.isConnected()) {
            controller.connect();
        } else {
            controller.disconnect(true);
        }
    }

    @Override
    public void dbModelsViewDidShowMenu(IMenuManager menuManager) {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() == null) {
            return;
        }
        DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        if (selectedController.isConnected()) {
            Action disconnectAction = new Action(Images.DISCONNECT_DATABASE.getDescription(resourceBundle),
                    ImageDescriptor.createFromImage(ImageUtils.getImage(Images.DISCONNECT_DATABASE))) {
                @Override
                public void run() {
                    disconnectSelectedDatabase();
                }
            };
            menuManager.add(disconnectAction);
        } else {
            Action connectAction = new Action(Images.CONNECT_DATABASE.getDescription(resourceBundle),
                    ImageDescriptor.createFromImage(ImageUtils.getImage(Images.CONNECT_DATABASE))) {
                @Override
                public void run() {
                    connectToSelectedDatabase();
                }
            };
            menuManager.add(connectAction);
            Action editAction = new Action(Images.EDIT_DATABASE.getDescription(resourceBundle),
                    ImageDescriptor.createFromImage(ImageUtils.getImage(Images.EDIT_DATABASE))) {
                @Override
                public void run() {
                    openEditSelectedDatabaseDialog();
                }
            };
            menuManager.add(editAction);
        }
        Action updateProcessesAction = new Action(Images.UPDATE.getDescription(resourceBundle),
                ImageDescriptor.createFromImage(ImageUtils.getImage(Images.UPDATE))) {
            @Override
            public void run() {
                updateProcessesInSelectedDatabase();
            }
        };
        menuManager.add(updateProcessesAction);
    }

    @Override
    public void settingsUpdatePeriodChanged(int updatePeriod) {
        if (settings.isAutoUpdate()) {
            dbControllers.stream().filter(DBController::isConnected).forEach(DBController::startProcessesUpdater);
        }
    }

    @Override
    public void settingsShowIdleChanged(boolean isShowIdle) {
        if (settings.isAutoUpdate()) {
            dbControllers.stream().filter(DBController::isConnected).forEach(DBController::startProcessesUpdater);
        }
    }

    @Override
    public void settingsAutoUpdateChanged(boolean isAutoUpdate) {
        if (isAutoUpdate) {
            dbControllers.stream().filter(DBController::isConnected).forEach(DBController::startProcessesUpdater);
        } else {
            dbControllers.stream().filter(DBController::isConnected).forEach(DBController::stopProcessesUpdater);
        }
    }

    @Override
    public void dataSourceFilterShowOnlyBlockedProcessesChanged(boolean showOnlyBlockedProcesses) {
        dbProcessesView.getTreeViewer().refresh();
    }

    @Override
    public void processesFiltersViewPidFilterConditionChanged(DBProcessesFiltersView view, FilterCondition condition) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getPidFilter().setCondition(condition);
        }
    }

    @Override
    public void processesFiltersViewPidFilterValueChanged(DBProcessesFiltersView view, Integer value) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getPidFilter().setValue(value);
        }
    }

    @Override
    public void processesFiltersViewQueryFilterConditionChanged(DBProcessesFiltersView view, FilterCondition condition) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getQueryFilter().setCondition(condition);
        }
    }

    @Override
    public void processesFiltersViewQueryFilterValueChanged(DBProcessesFiltersView view, String value) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getQueryFilter().setValue(value);
        }
    }

    @Override
    public void processesFiltersViewApplicationFilterConditionChanged(DBProcessesFiltersView view, FilterCondition condition) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getApplicationFilter().setCondition(condition);
        }
    }

    @Override
    public void processesFiltersViewApplicationFilterValueChanged(DBProcessesFiltersView view, String value) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getApplicationFilter().setValue(value);
        }
    }

    @Override
    public void processesFiltersViewDatabaseFilterConditionChanged(DBProcessesFiltersView view, FilterCondition condition) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getDatabaseFilter().setCondition(condition);
        }
    }

    @Override
    public void processesFiltersViewDatabaseFilterValueChanged(DBProcessesFiltersView view, String value) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getDatabaseFilter().setValue(value);
        }
    }

    @Override
    public void processesFiltersViewUserNameFilterConditionChanged(DBProcessesFiltersView view, FilterCondition condition) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getUserNameFilter().setCondition(condition);
        }
    }

    @Override
    public void processesFiltersViewUserNameFilterValueChanged(DBProcessesFiltersView view, String value) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getUserNameFilter().setValue(value);
        }
    }

    @Override
    public void processesFiltersViewClientFilterConditionChanged(DBProcessesFiltersView view, FilterCondition condition) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getClientFilter().setCondition(condition);
        }
    }

    @Override
    public void processesFiltersViewClientFilterValueChanged(DBProcessesFiltersView view, String value) {
        DBProcessFilter filter = getFilterForFiltersView(view);
        if (filter != null) {
            filter.getClientFilter().setValue(value);
        }
    }

    private DBProcessFilter getFilterForFiltersView(DBProcessesFiltersView view) {
        if (dbModelsView.getTableViewer().getStructuredSelection().getFirstElement() == null) {
            return null;
        }
        DBController selectedController = (DBController) dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        boolean isCurrentProcesses = view.equals(dbProcessesFiltersView);
        return isCurrentProcesses ? selectedController.getProcessesFilters() : selectedController.getBlocksJournal().getProcessesFilters();
    }

    @Override
    public void didSelectSortColumn(TreeColumn column, int sortDirection) {
        dbProcessesView.getTreeViewer().setComparator(new DBProcessesViewComparator((Columns) column.getData(), sortDirection));
    }

    private void dbProcessesViewSelectionChanged(SelectionChangedEvent event) {
        ISelection selection = event.getSelection();
        if (selection.isEmpty()) {
            dbProcessInfoView.hide();
        } else {
            IStructuredSelection structuredSelection = (IStructuredSelection)selection;
            selectedProcesses = (List<DBProcess>) structuredSelection.toList();
            dbProcessInfoView.show(selectedProcesses.get(0));
        }
    }

    private void dbBlocksJournalViewSelectionChanged(SelectionChangedEvent event) {
        ISelection selection = event.getSelection();
        if (selection.isEmpty()) {
            dbBlocksJournalProcessInfoView.hide();
        } else {
            DBProcess process;
            IStructuredSelection structuredSelection = (IStructuredSelection)selection;
            Object element = structuredSelection.getFirstElement();
            if (element instanceof DBBlocksJournalProcess) {
                DBBlocksJournalProcess blocksJournalProcess = (DBBlocksJournalProcess)element;
                process = blocksJournalProcess.getProcess();
            } else {
                process = (DBProcess)element;
            }
            dbBlocksJournalProcessInfoView.show(process);
        }
    }

    @Override
    public void dbProcessInfoViewTerminateProcessToolItemClicked() {
        Object selectedController = dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        if (selectedController == null || selectedProcesses.isEmpty()) {
            return;
        }

        List<Integer> pidProcessesList = selectedProcesses.stream().map(DBProcess::getPid).collect(Collectors.toList());
        if (settings.isConfirmRequired() && !MessageDialog.openQuestion(view.getShell(), l10n("confirm_action"),
                l10n("kill_process_confirm_message", pidProcessesList))) {
            return;
        }

        DBController selectedDbController = (DBController) selectedController;
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(Display.getDefault().getActiveShell());
        try {
            dialog.run(true, true, progressMonitor -> {
                progressMonitor.beginTask("Termination processes", pidProcessesList.size());
                for (Integer processPid : pidProcessesList) {
                    if (progressMonitor.isCanceled()) {
                        LOG.info(l10n("kill_process_cancelled_message"));
                        progressMonitor.done();
                        break;
                    } else {
                        tryTerminateProcess(selectedDbController, processPid);
                        progressMonitor.worked(1);
                    }
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            LOG.error(l10n("kill_process_error_message", e.getMessage()), e);
        } finally {
            selectedDbController.updateProcesses();
        }
    }

    private void tryTerminateProcess(DBController dbController, Integer pid) {
        try {
            boolean result = dbController.terminateProcessWithPid(pid);
            if (result) {
                LOG.info(l10n("process_terminated", dbController.getModel().getName(), pid)); 
            } else {
                LOG.info(l10n("process_not_terminated", dbController.getModel().getName(), pid, ""));
            }
        } catch (SQLException e) {
            LOG.error(l10n("process_not_terminated", dbController.getModel().getName(), pid, e.getMessage()), e);
        }
    }

    @Override
    public void dbProcessInfoViewCancelProcessToolItemClicked(){
        Object selectedController = dbModelsView.getTableViewer().getStructuredSelection().getFirstElement();
        if (selectedController == null || selectedProcesses.isEmpty()) {
            return;
        }

        List<Integer> pidProcessesList = selectedProcesses.stream().map(DBProcess::getPid).collect(Collectors.toList());
        if (settings.isConfirmRequired() && !MessageDialog.openQuestion(view.getShell(), l10n("confirm_action"),
                l10n("cancel_process_confirm_message", pidProcessesList))) {
            return;
        }

        DBController selectedDbController = (DBController) selectedController;
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(Display.getDefault().getActiveShell());
        try {
            dialog.run(true, true, progressMonitor -> {
                progressMonitor.beginTask("Cancel processes", pidProcessesList.size());
                for (Integer processPid : pidProcessesList) {
                    if (progressMonitor.isCanceled()) {
                        LOG.info(l10n("cancel_process_cancelled_message"));
                        progressMonitor.done();
                        break;
                    } else {
                        tryCancelProcess(selectedDbController, processPid);
                        progressMonitor.worked(1);
                    }
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            LOG.error(l10n("cancel_process_error_message", e.getMessage()), e);
        } finally {
            selectedDbController.updateProcesses();
        }
    }

    private void tryCancelProcess(DBController dbController, Integer pid) {
        try {
            boolean result = dbController.cancelProcessWithPid(pid);
            if (result) {
                LOG.info(l10n("process_cancelled", dbController.getModel().getName(), pid));
            } else {
                LOG.info(l10n("process_not_cancelled", dbController.getModel().getName(), pid, ""));
            }
        } catch (SQLException e) {
            LOG.error(l10n("process_not_cancelled", dbController.getModel().getName(), pid, e.getMessage()), e);
        }
    }

    private String l10n(String msgId, Object... objects) {
        return String.format(resourceBundle.getString(msgId), objects);
    }
}
