/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.client.console.wicket.markup.html.form;

import static de.agilecoders.wicket.jquery.JQuery.$;

import de.agilecoders.wicket.extensions.markup.html.bootstrap.form.fileinput.BootstrapFileInputField;
import de.agilecoders.wicket.extensions.markup.html.bootstrap.form.fileinput.FileInputConfig;
import de.agilecoders.wicket.extensions.markup.html.bootstrap.form.fileinput.FileinputJsReference;
import de.agilecoders.wicket.jquery.JQuery;
import de.agilecoders.wicket.jquery.function.IFunction;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.client.console.SyncopeWebApplication;
import org.apache.syncope.client.console.SyncopeConsoleSession;
import org.apache.syncope.client.ui.commons.Constants;
import org.apache.syncope.client.console.commons.PreviewUtils;
import org.apache.syncope.client.console.pages.BasePage;
import org.apache.syncope.client.ui.commons.HttpResourceStream;
import org.apache.syncope.client.ui.commons.markup.html.form.FieldPanel;
import org.apache.syncope.client.ui.commons.markup.html.form.preview.AbstractBinaryPreviewer;
import org.apache.syncope.client.ui.commons.markup.html.form.BaseBinaryFieldPanel;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.util.ListModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.lang.Bytes;

public class BinaryFieldPanel extends BaseBinaryFieldPanel {

    private static final long serialVersionUID = 6264462604183088931L;

    @SpringBean
    private PreviewUtils previewUtils;

    private final String mimeType;

    private final WebMarkupContainer container;

    private final AjaxLink<Void> downloadLink;

    private final Form<?> uploadForm;

    private final Fragment emptyFragment;

    private final BootstrapFileInputField fileUpload;

    private final BinaryFieldDownload fileDownload;

    private final AbstractBinaryPreviewer previewer;

    private final IndicatingAjaxLink<Void> resetLink;

    private final Bytes maxUploadSize;

    private final IModel<String> model;

    private final String fileKey;

    public BinaryFieldPanel(
            final String id,
            final String name,
            final IModel<String> model,
            final String mimeType,
            final String fileKey) {

        super(id, name, model);
        this.model = model;
        this.fileKey = fileKey;
        this.mimeType = mimeType;

        previewer = previewUtils.getPreviewer(mimeType);

        maxUploadSize = SyncopeWebApplication.get().getMaxUploadFileSizeMB() == null
                ? null
                : Bytes.megabytes(SyncopeWebApplication.get().getMaxUploadFileSizeMB());
        uploadForm = new StatelessForm<>("uploadForm");
        uploadForm.setMultiPart(true);
        add(uploadForm);

        container = new WebMarkupContainer("previewContainer") {

            private static final long serialVersionUID = 2628490926588791229L;

            @Override
            public void renderHead(final IHeaderResponse response) {
                if (previewer == null) {
                    FileinputJsReference.INSTANCE.renderHead(response);
                    final JQuery fileinputJS = $(fileUpload).chain(new IFunction() {

                        private static final long serialVersionUID = -2285418135375523652L;

                        @Override
                        public String build() {
                            return "fileinput({"
                                    + "'showRemove':false, "
                                    + "'showUpload':false, "
                                    + "'previewFileType':'any'})";
                        }
                    });
                    response.render(OnDomReadyHeaderItem.forScript(fileinputJS.get()));
                }
            }
        };
        container.setOutputMarkupId(true);

        emptyFragment = new Fragment("panelPreview", "emptyFragment", container);
        emptyFragment.setOutputMarkupId(true);
        container.add(emptyFragment);
        uploadForm.add(container);

        field = new TextField<>("textField", model);
        add(field.setLabel(new ResourceModel(name, name)).setOutputMarkupId(true));

        uploadForm.add(new Label("preview", StringUtils.isBlank(mimeType) ? StringUtils.EMPTY : '(' + mimeType + ')'));

        fileDownload = new BinaryFieldDownload(name, fileKey, mimeType, true) {

            private static final long serialVersionUID = 7203445884857810583L;

            @Override
            protected HttpResourceStream getResourceStream() {
                return new HttpResourceStream(buildResponse());
            }
        };

        add(fileDownload);

        downloadLink = new AjaxLink<Void>("downloadLink") {

            private static final long serialVersionUID = -4331619903296515985L;

            @Override
            public void onClick(final AjaxRequestTarget target) {
                try {
                    fileDownload.initiate(target);
                } catch (Exception e) {
                    SyncopeConsoleSession.get().onException(e);
                }
            }
        };
        downloadLink.setOutputMarkupId(true);
        uploadForm.add(downloadLink);

        FileInputConfig config = new FileInputConfig();
        config.showUpload(false);
        config.showRemove(false);
        config.showPreview(false);

        fileUpload = new BootstrapFileInputField("fileUpload", new ListModel<>(new ArrayList<>()), config);
        fileUpload.setOutputMarkupId(true);

        fileUpload.add(new AjaxFormSubmitBehavior(Constants.ON_CHANGE) {

            private static final long serialVersionUID = -1107858522700306810L;

            @Override
            protected void onSubmit(final AjaxRequestTarget target) {
                final FileUpload uploadedFile = fileUpload.getFileUpload();
                if (uploadedFile != null) {
                    if (maxUploadSize != null && uploadedFile.getSize() > maxUploadSize.bytes()) {
                        // SYNCOPE-1213 manage directly max upload file size (if set in properties file)
                        SyncopeConsoleSession.get().error(getString("tooLargeFile")
                                .replace("${maxUploadSizeB}", String.valueOf(maxUploadSize.bytes()))
                                .replace("${maxUploadSizeMB}", String.valueOf(maxUploadSize.bytes() / 1000000L)));
                        ((BasePage) getPageReference().getPage()).getNotificationPanel().refresh(target);
                    } else {
                        final byte[] uploadedBytes = uploadedFile.getBytes();
                        final String uploaded = Base64.getEncoder().encodeToString(uploadedBytes);
                        field.setModelObject(uploaded);
                        target.add(field);

                        if (previewer == null) {
                            container.addOrReplace(emptyFragment);
                        } else {
                            final Component panelPreview = previewer.preview(uploadedBytes);
                            changePreviewer(panelPreview);
                            fileUpload.setModelObject(null);
                            uploadForm.addOrReplace(fileUpload);
                        }

                        setVisibleFileButtons(StringUtils.isNotBlank(uploaded));
                        downloadLink.setEnabled(StringUtils.isNotBlank(uploaded));

                        target.add(uploadForm);
                    }
                }
            }
        });
        uploadForm.add(fileUpload);

        resetLink = new IndicatingAjaxLink<Void>("resetLink") {

            private static final long serialVersionUID = -7978723352517770644L;

            @Override
            public void onClick(final AjaxRequestTarget target) {
                field.setModelObject(null);
                target.add(field);
                downloadLink.setEnabled(false);
                container.addOrReplace(emptyFragment);
                setVisibleFileButtons(false);
                target.add(uploadForm);
            }

            @Override
            public String getAjaxIndicatorMarkupId() {
                return Constants.VEIL_INDICATOR_MARKUP_ID;
            }

        };
        uploadForm.add(resetLink);
    }

    private Response buildResponse() {
        return Response.ok(new ByteArrayInputStream(Base64.getMimeDecoder().decode(getModelObject()))).
                type(StringUtils.isBlank(mimeType) ? MediaType.APPLICATION_OCTET_STREAM : mimeType).
                header(HttpHeaders.LOCATION, StringUtils.EMPTY).
                build();
    }

    private void changePreviewer(final Component panelPreview) {
        final Fragment fragment = new Fragment("panelPreview", "previewFragment", container);
        fragment.add(panelPreview);
        container.addOrReplace(fragment);
        uploadForm.addOrReplace(container);
    }

    private void setVisibleFileButtons(final boolean visible) {
        resetLink.setVisible(visible);
        downloadLink.setVisible(visible);
    }

    @Override
    public BinaryFieldPanel clone() {
        LOG.debug("Custom clone for binary field panel...");
        return new BinaryFieldPanel(getId(), this.name, this.model, this.mimeType, this.fileKey);
    }

    @Override
    public FieldPanel<String> setNewModel(final IModel<String> model) {
        field.setModel(model);
        String modelObj = model.getObject();

        if (StringUtils.isNotBlank(modelObj)) {
            final Component panelPreview;
            if (previewer == null) {
                panelPreview = PreviewUtils.getDefaultPreviewer(mimeType);
            } else {
                panelPreview = previewer.preview(modelObj);
            }

            if (panelPreview != null) {
                changePreviewer(panelPreview);
            }
        }

        downloadLink.setEnabled(StringUtils.isNotBlank(modelObj));
        setVisibleFileButtons(StringUtils.isNotBlank(modelObj));
        return this;
    }

    @Override
    protected void sendError(final Exception exception) {
        SyncopeConsoleSession.get().onException(exception);
    }

    @Override
    protected Integer getMaxUploadFileSizeMB() {
        return SyncopeWebApplication.get().getMaxUploadFileSizeMB();
    }

    @Override
    public FieldPanel<String> setReadOnly(final boolean readOnly) {
        super.setReadOnly(readOnly);
        fileUpload.setEnabled(!readOnly);
        return this;
    }
}