package com.ambition.neoworld.upgrade;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** One-way key migration before FML corrects the file; FML owns backups and saving. */
public final class RetiredUpgradeConfig implements IConfigSpec {
    private final ModConfigSpec delegate;

    public RetiredUpgradeConfig(ModConfigSpec delegate) { this.delegate = delegate; }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public void validateSpec(ModConfig config) { delegate.validateSpec(config); }
    @Override public boolean isCorrect(UnmodifiableCommentedConfig config) {
        for (int level=1;level<=UpgradeData.MAX_LEVEL;level++)
            if (config.contains("upgrade.costs.goldForLevel"+level)) return false;
        return delegate.isCorrect(config);
    }
    @Override public void correct(CommentedConfig config) {
        for (int level=1;level<=UpgradeData.MAX_LEVEL;level++) {
            String oldKey="upgrade.costs.goldForLevel"+level;
            String newKey="upgrade.costs.silverForLevel"+level;
            if (config.contains(oldKey)) {
                Object old=config.remove(oldKey);
                if (!config.contains(newKey)) config.set(newKey,old);
            }
        }
        delegate.correct(config);
    }
    @Override public void acceptConfig(ILoadedConfig config) { delegate.acceptConfig(config); }
}
