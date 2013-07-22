package org.adempiere.ad.migration.xml.impl;

import java.util.Properties;

import org.adempiere.ad.migration.model.I_AD_Migration;
import org.adempiere.ad.migration.model.I_AD_MigrationStep;
import org.adempiere.ad.migration.service.IMigrationBL;
import org.adempiere.ad.migration.service.IMigrationDAO;
import org.adempiere.ad.migration.xml.IXMLHandler;
import org.adempiere.ad.migration.xml.IXMLHandlerFactory;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.POWrapper;
import org.adempiere.util.Services;
import org.compiere.util.CLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class MigrationHandler implements IXMLHandler<I_AD_Migration>
{
	private final transient CLogger logger = CLogger.getCLogger(getClass());

	public static final String NODENAME = "Migration";

	public static final String NODE_SeqNo = "SeqNo";
	public static final String NODE_Name = "Name";
	public static final String NODE_EntityType = "EntityType";
	public static final String NODE_ReleaseNo = "ReleaseNo";
	public static final String NODE_Comments = "Comments";
	public static final String NODE_Step = "Step";
	public static final String NODE_IsDeferredConstraints = I_AD_Migration.COLUMNNAME_IsDeferredConstraints;

	@Override
	public Node toXmlNode(final Document document, final I_AD_Migration migration)
	{
		logger.info("Exporting: " + Services.get(IMigrationBL.class).getSummary(migration));

		final Element root = document.createElement(NODENAME);

		root.setAttribute(NODE_SeqNo, Integer.toString(migration.getSeqNo()));
		root.setAttribute(NODE_Name, migration.getName());
		root.setAttribute(NODE_EntityType, migration.getEntityType());
		root.setAttribute(NODE_ReleaseNo, migration.getReleaseNo());
		root.setAttribute(NODE_IsDeferredConstraints, Boolean.toString(migration.isDeferredConstraints()));

		if (migration.getComments() != null)
		{
			Element comment = document.createElement(NODE_Comments);
			root.appendChild(comment);
			comment.appendChild(document.createTextNode(migration.getComments()));
		}

		for (final I_AD_MigrationStep step : Services.get(IMigrationDAO.class).retrieveSteps(migration, true))
		{
			if (!step.isActive())
			{
				logger.info("Skip inactive step: " + Services.get(IMigrationBL.class).getSummary(step));
				continue;
			}

			final Node stepNode = Services.get(IXMLHandlerFactory.class)
					.getHandler(I_AD_MigrationStep.class)
					.toXmlNode(document, step);

			if (stepNode != null)
			{
				root.appendChild(stepNode);
			}
		}

		return root;
	}

	@Override
	public boolean fromXmlNode(I_AD_Migration migration, Element element)
	{
		if (!NODENAME.equals(element.getLocalName()))
		{
			throw new AdempiereException("XML definition shall start with Migration node.");
		}

		final String name = element.getAttribute(NODE_Name);
		final int seqNo = Integer.parseInt(element.getAttribute(NODE_SeqNo));
		final String entityType = element.getAttribute(NODE_EntityType);
		final String releaseNo = element.getAttribute(NODE_ReleaseNo);
		final boolean deferrConstraints = Boolean.valueOf(element.getAttribute(NODE_IsDeferredConstraints));

		//
		// Check if there is an old migration
		final Properties ctx = POWrapper.getCtx(migration);
		final String trxName = POWrapper.getTrxName(migration);
		if (Services.get(IMigrationDAO.class).existsMigration(ctx, name, seqNo, entityType, trxName))
		{
			logger.info("Migration already imported [IGNORE]");
			return false;
		}

		migration.setName(name);
		migration.setSeqNo(seqNo);
		migration.setEntityType(entityType);
		migration.setReleaseNo(releaseNo);
		migration.setIsDeferredConstraints(deferrConstraints);

		// Comment
		final Node commentNode = (Element)element.getElementsByTagName(NODE_Comments).item(0);
		if (commentNode != null)
		{
			migration.setComments(commentNode.getTextContent());
		}

		POWrapper.save(migration);

		final NodeList children = element.getElementsByTagName(NODE_Step);
		for (int i = 0; i < children.getLength(); i++)
		{
			final Element stepNode = (Element)children.item(i);
			if (NODE_Step.equals(stepNode.getTagName()))
			{
				final I_AD_MigrationStep step = POWrapper.create(ctx, I_AD_MigrationStep.class, trxName);
				step.setAD_Migration_ID(migration.getAD_Migration_ID());

				Services.get(IXMLHandlerFactory.class)
						.getHandler(I_AD_MigrationStep.class)
						.fromXmlNode(step, stepNode);

				POWrapper.save(step);
			}
		}

		Services.get(IMigrationBL.class).updateStatus(migration);

		logger.info("Imported migration: " + Services.get(IMigrationBL.class).getSummary(migration));
		return true;
	}
}
