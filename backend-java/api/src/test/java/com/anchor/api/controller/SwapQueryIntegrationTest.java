package com.anchor.api.controller;

import com.anchor.api.bootstrap.ApiApplication;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.dataobject.SwapDO;
import com.anchor.common.dao.SwapMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ApiApplication.class, properties = "server.port=0")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SwapQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SwapMapper swapMapper;

    @Test
    void addsAddressAndListsSwap() throws Exception {
        String address = "0x71c86b408655373fb1953d4b6f0f239bb7b23e92";
        mockMvc.perform(post("/api/v1/watch-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chainId":8453,"address":"0x71C86B408655373fB1953D4b6F0F239bB7b23e92","name":"whale","tag":"WHALE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value(address))
                .andExpect(jsonPath("$.swapCount").value(0));

        SwapDO swap = new SwapDO();
        swap.setChainId(8453L);
        swap.setTxHash("0xabc");
        swap.setLogIndex(4);
        swap.setBlockNumber(50L);
        swap.setBlockHash("0xhash");
        swap.setBlockTimestamp(1_700_000_000L);
        swap.setTxFrom(address);
        swap.setTxTo("0x2626664c2603336e57b271c5c0b26f421741e481");
        swap.setDex(AnchorConstants.DEX_UNISWAP_V3);
        swap.setDirection("BUY");
        swap.setTokenIn("0x833589fcd6edb6e08f4c7c32d4f71b54bda02913");
        swap.setTokenOut("0x4200000000000000000000000000000000000006");
        swap.setAmountIn(new BigInteger("1000000"));
        swap.setAmountOut(new BigInteger("500000000000000"));
        swap.setConfirmStatus(ConfirmStatusEnum.CONFIRMING.name());
        swap.setWatchedAddress(address);
        swap.setCreatedBy("test");
        swap.setUpdatedBy("test");
        swapMapper.saveBatch(List.of(swap));

        mockMvc.perform(get("/api/v1/swaps").param("chainId", "8453").param("address", address).param("direction", "BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].txHash").value("0xabc"))
                .andExpect(jsonPath("$.items[0].blockNumber").value(50))
                .andExpect(jsonPath("$.items[0].tokenIn.amount").value("1000000"))
                .andExpect(jsonPath("$.items[0].tokenOut.address").value("0x4200000000000000000000000000000000000006"))
                .andExpect(jsonPath("$.items[0].direction").value("BUY"));
    }
}
