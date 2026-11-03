#!/usr/bin/env bash
# One-time setup of a fresh Oracle Cloud Ubuntu 24.04 Arm VM (safe to run again). Run as the "ubuntu" user:
#   curl -fsSL https://raw.githubusercontent.com/allevarun1/InfinityStore-E-Commerce-Shopping-Platform/main/deploy/oracle/setup-server.sh -o setup.sh
#   bash setup.sh
# It: updates the system and turns on automatic security updates, sets the time zone, adds swap, locks SSH to keys
# only, enables fail2ban, opens ports 80/443 in the VM's own firewall, installs Docker, clones the shop to
# /opt/rekha/app, creates .env from the example, and schedules the nightly backup.
# Ports 80/443 must ALSO be opened in Oracle's console (VCN > Security list); that part cannot be done from here.
set -Eeuo pipefail

REPO_URL="${REPO_URL:-https://github.com/allevarun1/InfinityStore-E-Commerce-Shopping-Platform.git}"
APP=/opt/rekha/app
KIT="$APP/deploy/oracle"

say() { printf '\n==> %s\n' "$*"; }

main() {
  [ "$(id -u)" -ne 0 ] || { echo "Run as the ubuntu user (it uses sudo), not as root."; exit 1; }
  export DEBIAN_FRONTEND=noninteractive

  say "System updates"
  echo "iptables-persistent iptables-persistent/autosave_v4 boolean true" | sudo debconf-set-selections
  echo "iptables-persistent iptables-persistent/autosave_v6 boolean true" | sudo debconf-set-selections
  sudo apt-get update -q
  sudo apt-get -y -q -o Dpkg::Options::=--force-confold upgrade
  sudo apt-get -y -q install git curl ca-certificates gzip cron nano util-linux unattended-upgrades fail2ban iptables-persistent
  printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' \
    | sudo tee /etc/apt/apt.conf.d/20auto-upgrades > /dev/null

  say "Time zone Asia/Kolkata (for cron and logs; the API itself keeps UTC as on Render)"
  sudo timedatectl set-timezone Asia/Kolkata

  say "4 GB swap (safety margin for Docker builds)"
  if ! sudo swapon --show | grep -q /swapfile; then
    sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile > /dev/null && sudo swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
  fi

  say "SSH: keys only, no root login (10- so it wins: sshd uses the first value it reads)"
  [ -s "$HOME/.ssh/authorized_keys" ] || { echo "No SSH key in ~/.ssh/authorized_keys: refusing to turn off passwords."; exit 1; }
  printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' \
    | sudo tee /etc/ssh/sshd_config.d/10-rekha.conf > /dev/null
  sudo sshd -t && sudo systemctl reload ssh
  sudo systemctl enable --now fail2ban > /dev/null

  say "Firewall on the VM: allow 80 and 443 (Oracle's image blocks them before its REJECT rule)"
  local port pos
  for port in 80 443; do
    if ! sudo iptables -C INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT 2> /dev/null; then
      pos="$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
      sudo iptables -I INPUT "${pos:-1}" -p tcp -m state --state NEW --dport "$port" -j ACCEPT
    fi
  done
  sudo netfilter-persistent save > /dev/null

  say "Docker"
  if ! command -v docker > /dev/null; then
    # Docker's official apt repository (not the convenience script).
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update -q
    sudo apt-get -y -q install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  fi
  printf '{\n  "log-driver": "json-file",\n  "log-opts": { "max-size": "10m", "max-file": "5" }\n}\n' \
    | sudo tee /etc/docker/daemon.json > /dev/null
  sudo systemctl enable docker > /dev/null && sudo systemctl restart docker
  sudo usermod -aG docker "$USER"

  say "The shop's code in $APP"
  sudo mkdir -p /opt/rekha/backups
  sudo chown -R "$USER:$USER" /opt/rekha
  if [ ! -d "$APP/.git" ]; then git clone --quiet "$REPO_URL" "$APP"; else git -C "$APP" pull --quiet --ff-only; fi
  chmod +x "$KIT"/*.sh
  if [ ! -f "$KIT/.env" ]; then
    cp "$KIT/.env.example" "$KIT/.env"
    chmod 600 "$KIT/.env"
    echo "Created $KIT/.env from the example: fill it in (nano $KIT/.env)."
  fi

  say "Nightly backup at 02:30 IST"
  ( crontab -l 2> /dev/null | grep -v 'rekha-backup' || true
    echo "30 2 * * * $KIT/backup.sh >> /opt/rekha/backups/backup.log 2>&1 # rekha-backup"
  ) | crontab -

  cat << EOF

Setup finished. Next:
  1. Log out and back in (so your user can run docker without sudo).
  2. nano $KIT/.env     fill in every value (copy from Render; see the comments in the file)
  3. $KIT/deploy.sh --force
  4. curl https://\$(grep ^API_DOMAIN= $KIT/.env | cut -d= -f2)/api/store/info
EOF
}

main "$@"
