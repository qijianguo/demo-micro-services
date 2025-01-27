package com.qijianguo.micro.services.user.interfaces.facade;

import com.qijianguo.micro.services.base.exception.EmBusinessError;
import com.qijianguo.micro.services.user.domain.captcha.entity.Phone;
import com.qijianguo.micro.services.user.domain.captcha.entity.PhonePolicy;
import com.qijianguo.micro.services.user.domain.captcha.exception.CaptchaEmBusinessError;
import com.qijianguo.micro.services.user.infrastructure.util.TimeUtils;
import com.qijianguo.micro.services.user.application.assembler.CaptchaAssembler;
import com.qijianguo.micro.services.user.application.dto.PhoneRequest;
import net.minidev.json.JSONValue;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.qijianguo.micro.services.user.domain.captcha.entity.PhonePolicy.Config.EXPIRED;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@FixMethodOrder(value = MethodSorters.NAME_ASCENDING)
public class CaptchaControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaControllerTest.class);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RedisTemplate redisTemplate;

    private static PhoneRequest request = null;

    private static Phone phone = null;

    @BeforeClass
    public static void beforeClass() {
        request = new PhoneRequest();
        request.setPhone("12345678996");
        request.setCaptchaImage("");
        phone = CaptchaAssembler.toPhone(request);
    }

    @Test
    public void test01_normal() throws Exception {
        // 正常请求
        normal();
    }

    @Test
    public void test02_frequency() throws Exception {
        // 验证频繁请求
        frequency();
    }

    @Test
    public void test03_countLimited() throws Exception {
        // 验证每日次数耗尽
        countLimited();
    }

    @Test
    public void test04_resetCount() throws Exception {
        // 重置今日次数
        resetCount();
    }

    private void normal() throws Exception {
        // 正常请求
        ResultActions result = phoneCode(request);
        result.andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(EmBusinessError.SUCCESS.getErrCode()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("SUCCESS"))
        ;
        String contentAsString = result.andReturn().getResponse().getContentAsString();
        logger.info("result: {}", contentAsString);
    }

    private void frequency() throws Exception {
        // 验证频繁请求
        ResultActions result2 = phoneCode(request);
        result2.andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(CaptchaEmBusinessError.CODE_REQ_PHONE_REQ_FREQUENCY.getErrCode()))
        ;
    }

    /**
     * 设置最近一次的发送时间
     */
    private void resetSendTime(boolean today) {
        if (today) {
            phone.setCount(PhonePolicy.Config.DAY_LIMITED.getNum());
            phone.setCreateTime(TimeUtils.before(new Date(), 30, TimeUnit.MINUTES));
            phone.setModifyTime(TimeUtils.before(new Date(), 2, TimeUnit.MINUTES));
            redisTemplate.opsForValue().set(phone.generateKey(), phone, EXPIRED.getNum(), EXPIRED.getTimeUnit());
        } else {
            phone.setCount(PhonePolicy.Config.DAY_LIMITED.getNum() + 1);
            phone.setCreateTime(TimeUtils.before(new Date(), 1, TimeUnit.DAYS));
            phone.setModifyTime(TimeUtils.before(new Date(), 1, TimeUnit.DAYS));
            redisTemplate.opsForValue().set(phone.generateKey(), phone, EXPIRED.getNum(), EXPIRED.getTimeUnit());
        }

    }

    public void countLimited() throws Exception {
        resetSendTime(true);
        // 验证每日次数耗尽
        ResultActions result3 = phoneCode(request);
        result3.andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value(CaptchaEmBusinessError.CODE_REQ_MAX_COUNTS.getErrCode()));
    }

    private void resetCount() throws Exception {
        // 重置今日次数
        resetSendTime(false);

        ResultActions result4 = phoneCode(request);
        result4.andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(EmBusinessError.SUCCESS.getErrCode()));

        Object o = redisTemplate.opsForValue().get(phone.generateKey());
        Assert.notNull(o, "缓存数据为空");

        Phone cache = (Phone)o;
        Assert.isTrue(cache.getCount().equals(1), "短信发送次数不正确");
    }


    private ResultActions phoneCode(PhoneRequest request) throws Exception {
        String paramJson = JSONValue.toJSONString(request);
        logger.info("params: {}", paramJson);
        ResultActions result = mockMvc.perform(MockMvcRequestBuilders
                .post("/captcha/phone")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paramJson)
        ).andDo(print());
        return result;
    }

}
