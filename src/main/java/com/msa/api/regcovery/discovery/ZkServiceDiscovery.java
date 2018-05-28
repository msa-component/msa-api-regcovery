package com.msa.api.regcovery.discovery;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.msa.api.regcovery.Constant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.ZkClient;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The type Zk service discovery.
 */
@Slf4j
@Data
public class ZkServiceDiscovery implements ServiceDiscovery {
    /**
     * The Zk address.
     */
    private String zkAddress;

    /**
     * The Address cache map.
     */
    private final Map<String, List<String>> addressCacheMap = Maps.newConcurrentMap();

    /**
     * The Zk client.
     */
    private ZkClient zkClient;

    /**
     * 服务发现
     * Discover string.
     *
     * @param name the name
     * @return the string
     */
    @Override
    public String discover(String name) {
        try {
            if (Objects.isNull(zkClient)) {
                zkClient = new ZkClient(zkAddress, Constant.ZK_SESSION_TIMEOUT, Constant.ZK_CONNECTION_TIMEOUT);
                log.debug(">>>>>>>>>===connect to zookeeper");
            }
            String servicePath = Constant.ZK_REGISTRY + "/" + name;
            // 获取service node
            if (!zkClient.exists(servicePath)) {
                throw new RuntimeException(String.format(">>>>>>>>>===can not find any service node on path {}", servicePath));
            }

            String address;
            List<String> addressCache = addressCacheMap.get(name);
            if (!CollectionUtils.isEmpty(addressCache)) {
                int addressCacheSize = addressCache.size();
                if (addressCacheSize == 1) {
                    address = addressCache.get(0);
                } else {
                    address = addressCache.get(ThreadLocalRandom.current().nextInt(addressCacheSize));
                    log.debug(">>>>>>>>>===get only address node: {}", address);
                }

                // 从zk服务注册中心获取某个服务地址
            } else {
                List<String> addressList = zkClient.getChildren(servicePath);
                List<String> addressCacheOfService = Lists.newCopyOnWriteArrayList();
                addressCacheOfService.addAll(addressList);
                addressCacheMap.put(name, addressCacheOfService);
                zkClient.subscribeChildChanges(servicePath, (parentPath, currentChilds) -> {
                    log.info(">>>>>>>>>===servicePath[{}] is changed", parentPath);
                    addressCacheMap.get(name).clear();
                    addressCacheMap.get(name).addAll(currentChilds);
                    addressCacheMap.put(name, addressCacheMap.get(name));
                });
                if (CollectionUtils.isEmpty(addressList)) {
                    throw new RuntimeException(String.format(">>>>>>>>>===can not find any address node on path {}", servicePath));
                }
                int nodes = addressList.size();
                if (nodes == 1) {
                    address = addressList.get(0);
                } else {
                    address = addressList.get(ThreadLocalRandom.current().nextInt(nodes));
                    log.debug(">>>>>>>>>===get only address node: {}", address);
                }
            }

            // 获取ip和端口号
            String addressPath = servicePath + "/" + address;
            String hostAndPort = zkClient.readData(addressPath);
            return hostAndPort;
        } catch (Exception e) {
            log.warn(">>>>>>>>>==={}", e.getMessage());
            try {
                zkClient = new ZkClient(zkAddress, Constant.ZK_SESSION_TIMEOUT, Constant.ZK_CONNECTION_TIMEOUT);
            } catch (Exception ex) {
                log.error(">>>>>>>>>===service discovery exception", e);
                zkClient.close();
            }
        }
        return null;
    }
}
