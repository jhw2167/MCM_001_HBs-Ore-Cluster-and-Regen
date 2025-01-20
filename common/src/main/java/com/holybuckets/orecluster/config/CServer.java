package com.holybuckets.orecluster.config;

import com.holybuckets.foundation.config.ConfigBase;

public class CServer extends ConfigBase {

	public final ConfigGroup base = group(0, "OreClusterRegenBaseValues", Comments.base);
	public final COreClusters cOreClusters = nested(0, COreClusters::new, Comments.clusters);

	@Override
	public String getName() {
		return "server";
	}

	private static class Comments {
		static String base = "Wrapper group for ore cluster regeneration configuration components";
		static String clusters = "Default cluster and regen period settings, applies to all ores specified in validOreClusterOreBlocks," +
		 " unless overridden by individual settings . To modify the cluster settings" +
		 " of a particular ore, use the file specified by the parameter 'oreClustersJsonConfigFilePath' below";
	}

}
