package com.bricktools.web;

import com.bricktools.backup.BackupService;
import com.bricktools.notification.NotificationService;
import com.bricktools.notification.NotificationService.Notification;
import com.bricktools.rebrickable.RebrickableService;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Maintenance endpoints: reference data updates, backups and notifications.
 */
@Tag(name = "Administration", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class AdminController {

  private final RebrickableService rebrickable;
  private final BackupService backup;
  private final NotificationService notifications;

  public AdminController(RebrickableService rebrickable, BackupService backup, NotificationService notifications) {
    this.rebrickable = rebrickable;
    this.backup = backup;
    this.notifications = notifications;
  }

  /**
   * Get the list of application notifications.
   */
  @GetMapping(path = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Notifications",
      description = "Returns all current notifications in the system."
  )
  public List<Notification> notifications() {
    return notifications.list();
  }

  /**
   * Check the status of the rebrickable sync service.
   */
  @GetMapping(path = "/rebrickable/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Rebrickable Status",
      description = "Returns information about the last successful download from rebrickable.com."
  )
  public Map<String, Object> rebrickableStatus() throws IOException {
    return rebrickable.status();
  }

  /**
   * Trigger a full data sync with rebrickable.com.
   */
  @PostMapping(path = "/rebrickable/update", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update Rebrickable Data",
      description = "Starts downloading the latest data from rebrickable.com."
  )
  public Map<String, Object> rebrickableUpdate() throws IOException, InterruptedException {
    return Map.of("lastDownload", rebrickable.update().toString());
  }

  /**
   * Get list of available backups and their details.
   */
  @GetMapping(path = "/backups", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List Backups",
      description = "Returns information about all existing data backups."
  )
  public List<Map<String, Object>> backups() throws IOException {
    return backup.list();
  }

  /**
   * Create a new backup of the application data.
   */
  @PostMapping(path = "/backups", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Create Backup",
      description = "Creates a new backup of all stored data."
  )
  public Map<String, Object> createBackup() throws IOException {
    return backup.create();
  }
}
