/*
 *
 * Copyright 2008-2012 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.search.solr;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.core.CoreContainer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.search.IndexEntry;
import org.springframework.data.search.QueryResponse;
import org.springframework.data.search.SearchTemplate;
import org.springframework.data.search.util.PathUtil;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

/**
 * Provides access to Solr search functionality in the familiar Spring style of
 * using data templates.
 * 
 * @author Vincent Devillers
 * @author Manish Baxi
 */
public class SolrTemplate extends SearchTemplate implements SolrOperations
{
    private static final String       DEFAULT_DOCUMENT_ID_FIELD = "id";
    private static final String       SCORE_FIELD               = "score";

    private boolean                   allowStreaming            = false;
    private boolean                   autoCommit                = true;
    private boolean                   autoGenerateIdField       = true;
    private String                    documentIdField           = DEFAULT_DOCUMENT_ID_FIELD;
    private StreamingUpdateSolrServer indexServer               = null;
    private int                       queueSize;
    private SolrServer                searchServer;
    private int                       threadCount;

    /**
     * Initializes a Solr server based on basic configuration information
     * available for the Solr server.
     * 
     * @param core The name of the Solr core to use.
     * @param embedded If <code>true</code>, an {@link EmbeddedSolrServer}
     *            instance is created.
     * @param paths Paths to the Solr server. If <code>embedded</code> is
     *            <code>true</code>, this should contain exactly one path. A
     *            {@link RuntimeException} will be thrown if
     *            <code>embedded</code> is <code>true</code> and this parameter
     *            has more than one path. If <code>embedded</code> is
     *            <code>false</code>, this can contain one or more paths. A
     *            {@link CommonsHttpSolrServer} instance is created if only one
     *            path is passed, otherwise an {@link LBHttpSolrServer} instance
     *            is created with all the paths.
     * @throws IOException If no Solr configuration can be found at the location
     *             pointed to by <code>path</code>.
     * @throws MalformedURLException If any of the <code>paths</code> values is
     *             malformed.
     * @throws ParserConfigurationException If the Solr configuration at any of
     *             the locations pointed to by <code>paths</code> cannot be
     *             parsed correctly.
     * @throws SAXException If the Solr configuration at any of the locations
     *             pointed to by <code>paths</code> contains invalid XML.
     */
    public SolrTemplate(final String core, final boolean embedded, final String[] paths) throws IOException, MalformedURLException, ParserConfigurationException, SAXException
    {
        this(createSolrServer(core, embedded, paths));
    }

    /**
     * Sets a {@link SolrServer} to use for indexing search entries.
     * 
     * @param server A {@link SolrServer}.
     */
    private SolrTemplate(final SolrServer server)
    {
        super();

        this.searchServer = server;
        this.setExceptionTranslator(new SolrExceptionTranslator());
    }

    /**
     * Allows streaming search entries to the Solr server if the template
     * configuration has been set to enforce streaming.
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        super.afterPropertiesSet();

        if (this.allowStreaming)
        {
            if (this.searchServer instanceof CommonsHttpSolrServer)
            {
                this.indexServer = new StreamingUpdateSolrServer(((CommonsHttpSolrServer) this.searchServer).getBaseURL(), this.queueSize, this.threadCount);
            }
            else
            {
                throw new IllegalArgumentException("Cannot allow streaming update on solr server other than CommonsHttpSolrServer instance!");
            }
        }

        Assert.hasText(documentIdField, "The document id field have to be setted");
    }

    /**
     * Builds a new index entry that can be added to a Solr search index.
     */
    @Override
    protected IndexEntry buildIndexEntry()
    {
        return new SolrIndexEntry(new SolrDocument());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String add(final IndexEntry document)
    {
        String id = addIdToDocumentIfEnabled(document);
        org.apache.solr.common.SolrDocument solrDocument = new org.apache.solr.common.SolrDocument();
        solrDocument.putAll(document);
        try
        {
            addDocument(solrDocument);
            if (isAutoCommit())
            {
                commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> add(final Collection<IndexEntry> documents)
    {
        org.apache.solr.common.SolrDocument solrDocument;
        List<String> ids = new ArrayList<String>(documents.size());

        for (IndexEntry document : documents)
        {
            ids.add(addIdToDocumentIfEnabled(document));
            solrDocument = new org.apache.solr.common.SolrDocument();
            solrDocument.putAll(document);
            try
            {
                addDocument(solrDocument);
            }
            catch (Exception e)
            {
                throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
            }
        }

        try
        {
            if (isAutoCommit())
            {
                commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit()
    {
        try
        {
            searchServer.commit();
            if (allowStreaming)
            {
                indexServer.commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final String id)
    {
        try
        {
            if (allowStreaming)
            {
                indexServer.deleteById(id);
            }
            else
            {
                searchServer.deleteById(id);
            }
            if (isAutoCommit())
            {
                commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final Collection<String> ids)
    {
        try
        {
            if (allowStreaming)
            {
                indexServer.deleteById(new ArrayList<String>(ids));
            }
            else
            {
                searchServer.deleteById(new ArrayList<String>(ids));
            }
            if (isAutoCommit())
            {
                commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAll()
    {
        this.deleteByQuery("*:*");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteByQuery(final String query)
    {
        try
        {
            if (allowStreaming)
            {
                indexServer.deleteByQuery(query);
            }
            else
            {
                searchServer.deleteByQuery(query);
            }
            if (isAutoCommit())
            {
                commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAlive()
    {
        try
        {
            SolrPingResponse pingResponse;
            pingResponse = searchServer.ping();
            return pingResponse.getStatus() == 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QueryResponse query(final String query) throws DataAccessException
    {
        final SolrQuery solrQuery = new SolrQuery(query);
        org.apache.solr.client.solrj.response.QueryResponse solrQueryResponse = null;

        try
        {
            solrQueryResponse = searchServer.query(solrQuery);
        }
        catch (SolrServerException e)
        {
            throw this.potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }

        final SolrQueryResponse queryResponse = new SolrQueryResponse();

        queryResponse.setNativeResponse(solrQueryResponse);

        queryResponse.setElapsedTime(solrQueryResponse.getElapsedTime());

        if (solrQueryResponse != null)
        {
            final SolrDocumentList results = solrQueryResponse.getResults();
            final List<SolrIndexEntry> documents = new ArrayList<SolrIndexEntry>(results.size());
            for (SolrDocument solrDocument : results)
            {
                final SolrIndexEntry document = new SolrIndexEntry(solrDocument);
                document.setScore((Float) solrDocument.get(SCORE_FIELD));
                documents.add(document);
            }
            queryResponse.setMatchingEntries(documents);
        }

        return queryResponse;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(String query)
    {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateInBatch(String query)
    {
    }

    private String addIdToDocumentIfEnabled(IndexEntry document)
    {
        String id = String.valueOf(document.get(documentIdField));
        if (!StringUtils.hasText(id) && autoGenerateIdField)
        {
            id = UUID.randomUUID().toString();
            document.put(documentIdField, id);
        }
        return id;
    }

    public boolean isAutoCommit()
    {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit)
    {
        this.autoCommit = autoCommit;
    }

    @Override
    public SolrServer getSolrServer()
    {
        return searchServer;
    }

    @Override
    public void refresh()
    {
        try
        {
            searchServer.optimize();
            if (allowStreaming)
            {
                indexServer.commit();
            }
        }
        catch (Exception e)
        {
            throw potentiallyConvertCheckedException(new RuntimeException(e.getCause()));
        }
    }

    private UpdateResponse addDocument(org.apache.solr.common.SolrDocument solrDocument) throws SolrServerException, IOException
    {
        UpdateResponse updateResponse;
        if (allowStreaming)
        {
            updateResponse = indexServer.add(ClientUtils.toSolrInputDocument(solrDocument));
        }
        else
        {
            updateResponse = searchServer.add(ClientUtils.toSolrInputDocument(solrDocument));
        }
        return updateResponse;
    }

    public void setSearchServer(SolrServer searchServer)
    {
        this.searchServer = searchServer;
    }

    public void setAllowStreaming(boolean allowStreaming)
    {
        this.allowStreaming = allowStreaming;
    }

    public void setQueueSize(int queueSize)
    {
        this.queueSize = queueSize;
    }

    public void setThreadCount(int threadCount)
    {
        this.threadCount = threadCount;
    }

    public void setDocumentIdField(String documentIdField)
    {
        this.documentIdField = documentIdField;
    }

    public void setAutoGenerateIdField(boolean autoGenerateIdField)
    {
        this.autoGenerateIdField = autoGenerateIdField;
    }

    /**
     * Initializes a Solr server based on basic configuration information
     * available for the Solr server.
     * 
     * @param core The name of the Solr core to use.
     * @param embedded If <code>true</code>, an {@link EmbeddedSolrServer}
     *            instance is created.
     * @param paths Paths to the Solr server. If <code>embedded</code> is
     *            <code>true</code>, this should contain exactly one path. A
     *            {@link RuntimeException} will be thrown if
     *            <code>embedded</code> is <code>true</code> and this parameter
     *            has more than one path. If <code>embedded</code> is
     *            <code>false</code>, this can contain one or more paths. A
     *            {@link CommonsHttpSolrServer} instance is created if only one
     *            path is passed, otherwise an {@link LBHttpSolrServer} instance
     *            is created with all the paths.
     * @throws IOException If no Solr configuration can be found at the location
     *             pointed to by <code>path</code>.
     * @throws MalformedURLException If any of the <code>paths</code> values is
     *             malformed.
     * @throws ParserConfigurationException If the Solr configuration at any of
     *             the locations pointed to by <code>paths</code> cannot be
     *             parsed correctly.
     * @throws SAXException If the Solr configuration at any of the locations
     *             pointed to by <code>paths</code> contains invalid XML.
     */
    private static SolrServer createSolrServer(final String core, final boolean embedded, final String[] paths) throws IOException, MalformedURLException,
            ParserConfigurationException, SAXException
    {
        if (paths == null)
        {
            throw new RuntimeException("Argument [paths] is null.");
        }
        else if (paths[0] == null)
        {
            throw new RuntimeException("Argument [paths] contains a null path.");
        }
        else if (paths[0].trim().equals(""))
        {
            throw new RuntimeException("Argument [paths] contains a blank path.");
        }

        if (embedded)
        {
            if (paths.length > 1)
            {
                throw new RuntimeException("Argument [embedded] is set to [true] but the argument [paths] contains more than one path.");
            }
            else if (core == null)
            {
                throw new RuntimeException("Argument [embedded] is set to [true] but the argument [coreName] contains a null value.");
            }
            else if (core.trim().equals(""))
            {
                throw new RuntimeException("Argument [embedded] is set to [true] but the argument [coreName] contains a blank value.");
            }
            else
            {
                final File configFile = new ClassPathResource(paths[0].trim()).getFile();
                final CoreContainer coreContainer = new CoreContainer();
                coreContainer.load(configFile.getParentFile().getAbsolutePath(), configFile);

                return new EmbeddedSolrServer(coreContainer, core);
            }
        }
        else if (paths.length > 1)
        {
            return new LBHttpSolrServer(paths);
        }

        return new CommonsHttpSolrServer(PathUtil.concatenate('/', paths[0], core));
    }
}
