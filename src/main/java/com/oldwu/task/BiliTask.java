package com.oldwu.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bili.BiliTaskMain;
import com.bili.dao.AutoBilibiliDao;
import com.bili.dao.BiliUserDao;
import com.bili.model.AutoBilibili;
import com.bili.model.BiliPlan;
import com.bili.model.BiliUser;
import com.bili.model.task.BiliData;
import com.bili.service.BiliService;
import com.oldwu.dao.AutoLogDao;
import com.oldwu.entity.AutoLog;
import com.oldwu.entity.TaskResult;
import com.push.PushUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component("biliTask")
public class BiliTask {
    private static AutoBilibiliDao bilibiliDao;
    private static BiliUserDao biliUserDao;
    private static AutoLogDao logDao;
    private static BiliService biliService;
    private final Log logger = LogFactory.getLog(BiliTask.class);

    @Autowired
    public void getBiliService(BiliService service) {
        BiliTask.biliService = service;
    }

    @Autowired
    public void getLogDao(AutoLogDao logDao) {
        BiliTask.logDao = logDao;
    }

    @Autowired
    public void getBiliDao(AutoBilibiliDao bilibiliDao) {
        BiliTask.bilibiliDao = bilibiliDao;
    }

    @Autowired
    public void getBiliUserDao(BiliUserDao biliUserDao) {
        BiliTask.biliUserDao = biliUserDao;
    }

    public void resetStatus() {
        //重置自动任务的标识
        //bili
        List<BiliPlan> biliPlans = biliUserDao.selectAll();
        for (BiliPlan biliPlan : biliPlans) {
            int autoId = biliPlan.getAutoId();
            BiliUser biliUser = new BiliUser();
            biliUser.setAutoId(autoId);
            biliUser.setStatus("100");
            biliUserDao.updateByAutoIdSelective(biliUser);
        }
    }

    /**
     * b站定时签到任务
     */
    public void doAutoCheck() {

        List<AutoBilibili> autoBilibilis = bilibiliDao.selectList(new QueryWrapper<>());

        for (AutoBilibili autoBilibili : autoBilibilis) {

            Integer auto_id = autoBilibili.getId();
            Integer userid = autoBilibili.getUserid();
            BiliUser userb = biliUserDao.selectByAutoId(auto_id);

            //任务未开启或已经完成，下一个
            if (Boolean.parseBoolean(autoBilibili.getSkipdailytask())) {
                BiliUser biliUser = new BiliUser(auto_id, "0", new Date());
                biliUserDao.updateByAutoIdSelective(biliUser);
                continue;
            }

            //已完成的任务不再重复执行
            if (userb.getStatus().equals("200")) {
                continue;
            }

            //更新任务状态为正在执行
            BiliUser biliUser = new BiliUser(auto_id, "1", null);
            biliUserDao.updateByAutoIdSelective(biliUser);

            //校验用户信息
            boolean b = biliService.userCheck(autoBilibili);
            if (!b) {
                biliUser.setStatus("500");
                biliUser.setEnddate(new Date());
                biliUserDao.updateByAutoIdSelective(biliUser);
                continue;
            }

            BiliTaskMain biliTaskMain = new BiliTaskMain();
            TaskResult taskResult = biliTaskMain.run(autoBilibili);
            if (taskResult.getIsTaskSuccess() == 1) {
                //任务成功
                BiliData biliData = (BiliData) taskResult.getData();
                //更新用户信息
                biliService.updateUserInfo(autoBilibili.getId(), biliData, true);
            }

            //执行推送任务，推送使用简易消息，而不是长篇大论
            PushUtil.doPush(taskResult.getMsg(), autoBilibili.getWebhook(), userid);

            AutoLog bilibili = new AutoLog(auto_id, "bili", taskResult.getIsTaskSuccess() == 1 ? "200" : taskResult.isUserCheckSuccess() ? "500" : "501", userid, new Date(), taskResult.getLog());
            logDao.insert(bilibili);

            //更新任务状态
            biliUser.setEnddate(new Date());
            biliUser.setStatus(taskResult.getIsTaskSuccess() == 1 ? "200" : taskResult.isUserCheckSuccess() ? "500" : "501");
            biliUserDao.updateByAutoIdSelective(biliUser);
        }
    }

}
