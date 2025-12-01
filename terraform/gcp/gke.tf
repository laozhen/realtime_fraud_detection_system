# GKE Cluster (optional - can use existing cluster)

resource "google_container_cluster" "fraud_detection" {
  count    = var.create_gke_cluster ? 1 : 0
  name     = var.gke_cluster_name
  location = var.gcp_region
  
  # We can't create a cluster with no node pool defined, but we want to only use
  # separately managed node pools. So we create the smallest possible default
  # node pool and immediately delete it.
  remove_default_node_pool = true
  initial_node_count       = 1
  
  # Workload Identity
  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }
  
  # Network policy
  network_policy {
    enabled = true
  }
  
  # Enable Stackdriver logging and monitoring
  logging_service    = var.enable_stackdriver_logging ? "logging.googleapis.com/kubernetes" : "none"
  monitoring_service = "monitoring.googleapis.com/kubernetes"
  
  # Maintenance window
  maintenance_policy {
    daily_maintenance_window {
      start_time = "03:00"
    }
  }
  
  # Release channel
  release_channel {
    channel = "REGULAR"
  }
  
  addons_config {
    http_load_balancing {
      disabled = false
    }
    horizontal_pod_autoscaling {
      disabled = false
    }
  }
}

resource "google_container_node_pool" "fraud_detection_nodes" {
  count      = var.create_gke_cluster ? 1 : 0
  name       = "fraud-detection-node-pool"
  location   = var.gcp_region
  cluster    = google_container_cluster.fraud_detection[0].name
  node_count = var.gke_node_count
  
  autoscaling {
    min_node_count = 1
    max_node_count = 5
  }
  
  node_config {
    machine_type = var.gke_machine_type
    
    # Google recommends custom service accounts with minimal permissions
    service_account = google_service_account.gke_nodes[0].email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
    
    # Workload Identity
    workload_metadata_config {
      mode = "GKE_METADATA"
    }
    
    labels = merge(
      var.labels,
      {
        environment = var.environment
      }
    )
    
    tags = ["fraud-detection-node"]
  }
  
  management {
    auto_repair  = true
    auto_upgrade = true
  }
}

# Service account for GKE nodes
resource "google_service_account" "gke_nodes" {
  count        = var.create_gke_cluster ? 1 : 0
  account_id   = "gke-nodes-${var.environment}"
  display_name = "GKE Nodes Service Account"
}

resource "google_project_iam_member" "gke_nodes_log_writer" {
  count   = var.create_gke_cluster ? 1 : 0
  project = var.gcp_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.gke_nodes[0].email}"
}

resource "google_project_iam_member" "gke_nodes_metric_writer" {
  count   = var.create_gke_cluster ? 1 : 0
  project = var.gcp_project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.gke_nodes[0].email}"
}

resource "google_project_iam_member" "gke_nodes_monitoring_viewer" {
  count   = var.create_gke_cluster ? 1 : 0
  project = var.gcp_project_id
  role    = "roles/monitoring.viewer"
  member  = "serviceAccount:${google_service_account.gke_nodes[0].email}"
}

