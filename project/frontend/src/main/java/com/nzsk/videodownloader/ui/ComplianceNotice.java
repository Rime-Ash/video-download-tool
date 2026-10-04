package com.nzsk.videodownloader.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 合规声明内容。启动时不再弹窗，改为常驻显示在“设置 - 环境检测”下方。
 */
final class ComplianceNotice {
    private static final String INTRO =
            "本软件仅用于下载您依法有权保存、且当前账号能够正常观看的公开视频。";
    private static final List<String> RULES = List.of(
            "禁止下载付费、私密、受限、DRM 保护或需要绕过鉴权的内容。",
            "禁止绕过安全机制、删除水印或版权信息、批量抓取和账号遍历。",
            "仅支持 bilibili.com、b23.tv、douyin.com、v.douyin.com、iesdouyin.com。",
            "Cookie 仅用于本人已有的正常访问权限，不会上传或复制到临时目录。");

    private ComplianceNotice() {
    }

    static Node content() {
        VBox box = new VBox(6);
        box.getChildren().add(wrapped(INTRO));
        for (String rule : RULES) {
            box.getChildren().add(wrapped("• " + rule));
        }
        return box;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
