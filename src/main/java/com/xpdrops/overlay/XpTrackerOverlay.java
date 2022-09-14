package com.xpdrops.overlay;

import com.xpdrops.config.XpDropsConfig;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class XpTrackerOverlay extends Overlay
{
	private final XpDropsConfig config;
	private final XpDropOverlayManager xpDropOverlayManager;
	private final XpDropFontHandler xpDropFontHandler = new XpDropFontHandler();
	private static final int PROGRESS_BAR_HEIGHT = 6;
	private static final Color JAGEX_WIDGET_BACKGROUND_COLOR = new Color(90, 82, 69);

	@Inject
	private Client client;

	private final XpTrackerService xpTrackerService;

	@Inject
	private XpTrackerOverlay(XpDropsConfig config, XpDropOverlayManager xpDropOverlayManager, XpTrackerService xpTrackerService)
	{
		this.config = config;
		this.xpDropOverlayManager = xpDropOverlayManager;
		this.xpTrackerService = xpTrackerService;
		setLayer(OverlayLayer.UNDER_WIDGETS);
		setPosition(OverlayPosition.TOP_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Dimension dimension = null;
		if (config.useXpTracker())
		{
			xpDropFontHandler.updateFont(config.xpTrackerFontName(), config.xpTrackerFontSize(), config.xpTrackerFontStyle());
			XpDropOverlayUtilities.setGraphicsProperties(graphics);
			xpDropFontHandler.handleFont(graphics);

			Skill currentSkill = xpDropOverlayManager.getLastSkill();
			long xp = getSkillExperience(currentSkill);
			int icon = getSkillIconIndex(currentSkill);
			int width = graphics.getFontMetrics().stringWidth(XpDropOverlayManager.XP_FORMAT_PATTERN);
			int height = graphics.getFontMetrics().getHeight(); // ignores the size of the icon

			if (xpDropOverlayManager.isShouldDraw())
			{
				Dimension trackerDimensions = drawXpTracker(graphics, icon, xp);
				width = (int) trackerDimensions.getWidth();
				if (config.showXpTrackerProgressBar())
				{
					final int startGoalXp = xpTrackerService.getStartGoalXp(currentSkill);
					final int endGoalXp = xpTrackerService.getEndGoalXp(currentSkill);
					int barHeight = drawProgressBar(graphics, 0, (int) trackerDimensions.getHeight() + 1, width, startGoalXp, endGoalXp, xp);
					height = (int) (trackerDimensions.getHeight() + barHeight);
				}
			}

			dimension = new Dimension(width, height);
		}
		return dimension;
	}

	private long getSkillExperience(Skill skill)
	{
		long xp;
		if (Skill.OVERALL.equals(skill))
		{
			xp = client.getOverallExperience();
		}
		else
		{
			xp = client.getSkillExperience(skill);
		}
		return xp;
	}

	private int getSkillIconIndex(Skill skill)
	{
		return skill.ordinal();
	}

	private int getAlpha()
	{
		int alpha = 0xff;
		if (config.xpTrackerClientTicksToLinger() != 0)
		{
			long deltaTime = System.currentTimeMillis() - xpDropOverlayManager.getLastSkillSetMillis();
			long deltaClientTicks = deltaTime / 20;
			if (config.xpTrackerFadeOut())
			{
				int delta = Math.min(33, (int) (0.33f * config.xpTrackerClientTicksToLinger()));
				int threshold = config.xpTrackerClientTicksToLinger() - delta;
				int point = (int) (deltaClientTicks - threshold);
				float fade = Math.max(0.0f, Math.min(1.0f, point / (float) delta));
				alpha = (int) Math.max(0, 0xff - fade * 0xff);
			}
			else if (deltaClientTicks > config.xpTrackerClientTicksToLinger())
			{
				alpha = 0;
			}
		}
		return alpha;
	}

	private Dimension drawXpTracker(Graphics2D graphics, int icon, long experience)
	{
		String text = XpDropOverlayManager.XP_FORMATTER.format(experience);

		int textY = graphics.getFontMetrics().getMaxAscent();
		int textWidth = graphics.getFontMetrics().stringWidth(text);

		int imageY = textY - graphics.getFontMetrics().getMaxAscent(); // 0

		int alpha = getAlpha();
		//Adding 5 onto image width to give a little space in between icon and text
		Dimension iconDimensions = drawIcon(graphics, icon, 0, imageY, alpha);
		int imageWidth = (int) (iconDimensions.getWidth() + 5);

		drawText(graphics, text, imageWidth, textY, alpha);

		return new Dimension(textWidth + imageWidth, (int)Math.max(graphics.getFontMetrics().getHeight(), iconDimensions.getHeight()));
	}

	// Returns height of drawn bar.
	private int drawProgressBar(Graphics2D graphics, int x, int y, int width, long start, long end, long _current)
	{
		if (start < 0 || end < 0 || start == end)
		{
			// No point in drawing a bar.
			return 0;
		}

		long total = end - start;
		double ratio = 1.0;
		long current = Math.max(0, _current - start);
		if (total > 0)
		{
			ratio = current / (double)total;
		}

		int alpha = getAlpha();

		int progressBarWidth = (int) (ratio * (width - 4));
		int barHeight = PROGRESS_BAR_HEIGHT;

		Color jagexBackgroundColor = new Color(JAGEX_WIDGET_BACKGROUND_COLOR.getRed(), JAGEX_WIDGET_BACKGROUND_COLOR.getGreen(), JAGEX_WIDGET_BACKGROUND_COLOR.getBlue(), alpha);
		graphics.setColor(jagexBackgroundColor);
		graphics.fillRect(x, y, width, barHeight + 2);

		Color blackBackgroundColor = new Color(0, 0, 0, alpha);
		graphics.setColor(blackBackgroundColor);
		graphics.fillRect(x + 1, y + 1, width - 2, barHeight);

		final double rMod = 130.0 * ratio;
		final double gMod = 255.0 * ratio;
		final Color c = new Color((int) (255 - rMod), (int) (0 + gMod), 0, alpha);
		graphics.setColor(c);
		graphics.fillRect(x + 2, y + 2, progressBarWidth, barHeight - 2);
		return PROGRESS_BAR_HEIGHT;
	}

	private Dimension drawIcon(Graphics2D graphics, int icon, int x, int y, float alpha)
	{
		int iconSize = graphics.getFontMetrics().getHeight();
		if (config.xpTrackerIconSizeOverride() > 0)
		{
			iconSize = config.xpTrackerIconSizeOverride();
		}
		BufferedImage image;

		if (config.showIconsXpTracker())
		{
			image = XpDropOverlayManager.getSTAT_ICONS()[icon];
			int _iconSize = Math.max(iconSize, 18);
			int iconWidth = image.getWidth() * _iconSize / 25;
			int iconHeight = image.getHeight() * _iconSize / 25;
			Dimension dimension = drawIcon(graphics, image, x, y, iconWidth, iconHeight, alpha / 0xff);

			return dimension;
		}
		return new Dimension(0,0);
	}

	private Dimension drawIcon(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height, float alpha)
	{
		int yOffset = graphics.getFontMetrics().getHeight() / 2 - height / 2;

		Composite composite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		graphics.drawImage(image, x, y + yOffset, width, height, null);
		graphics.setComposite(composite);
		return new Dimension(width, height);
	}

	private void drawText(Graphics2D graphics, String text, int textX, int textY, int alpha)
	{
		Color _color = config.xpTrackerColor();
		Color backgroundColor = new Color(0, 0, 0, alpha);
		Color color = new Color(_color.getRed(), _color.getGreen(), _color.getBlue(), alpha);
		graphics.setColor(backgroundColor);
		graphics.drawString(text, textX + 1, textY + 1);
		graphics.setColor(color);
		graphics.drawString(text, textX, textY);
	}
}
