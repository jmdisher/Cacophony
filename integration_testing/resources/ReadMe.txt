These are the config and swarm files for a private IPFS cluster on localhost to run integration tests on a complete cluster with multiple nodes.

To use this:
-init 2 repos
-copy the swarm into both repos
-copy the config files over the corresponding configs in each repo

Example:
-mkdir seed
-IPFS_PATH=seed /mnt/data/ipfs/kubo/ipfs init
-cp swarm.key seed/
-cp seed_config seed/config
-mkdir node1
-IPFS_PATH=node1 /mnt/data/ipfs/kubo/ipfs init
-cp swarm.key node1/
-cp node1_config node1/config

