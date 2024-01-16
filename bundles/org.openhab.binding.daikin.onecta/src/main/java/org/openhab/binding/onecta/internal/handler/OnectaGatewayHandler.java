/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.onecta.internal.handler;

import static org.openhab.binding.onecta.internal.OnectaGatewayConstants.*;

import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.onecta.internal.OnectaConfiguration;
import org.openhab.binding.onecta.internal.api.Enums;
import org.openhab.binding.onecta.internal.service.ChannelsRefreshDelay;
import org.openhab.binding.onecta.internal.service.DataTransportService;
import org.openhab.core.library.types.*;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OnectaGatewayHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Drent - Initial contribution
 */
@NonNullByDefault
public class OnectaGatewayHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OnectaGatewayHandler.class);

    private @Nullable OnectaConfiguration config;

    private @Nullable ScheduledFuture<?> pollingJob;

    private final DataTransportService dataTransService;
    private @Nullable ChannelsRefreshDelay channelsRefreshDelay;

    public OnectaGatewayHandler(Thing thing) {
        super(thing);
        dataTransService = new DataTransportService(thing.getConfiguration().get("unitID").toString(),
                Enums.ManagementPoint.GATEWAY);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        try {
            channelsRefreshDelay.add(channelUID.getId());

            updateStatus(ThingStatus.ONLINE);
        } catch (Exception ex) {
            // catch exceptions and handle it in your binding
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, ex.getMessage());
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(OnectaConfiguration.class);
        channelsRefreshDelay = new ChannelsRefreshDelay(
                Long.parseLong(thing.getConfiguration().get("refreshDelay").toString()) * 1000);
        // DataTransportService dataTransService = new DataTransportService();
        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly, i.e. any network access must be done in
        // the background initialization below.
        // Also, before leaving this method a thing status from one of ONLINE, OFFLINE or UNKNOWN must be set. This
        // might already be the real thing status in case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });

        // thing.setProperty(CHANNEL_GW_NAME, "");

        // These logging types should be primarily used by bindings
        // logger.trace("Example trace message");
        // logger.debug("Example debug message");
        // logger.warn("Example warn message");
        //
        // Logging to INFO should be avoided normally.
        // See https://www.openhab.org/docs/developer/guidelines.html#f-logging

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    public void refreshDevice() {
        logger.debug("refreshGateway :" + dataTransService.getUnitName());
        dataTransService.refreshUnit();

        if (dataTransService.isAvailable()) {

            // getThing().setLabel(String.format("Daikin Onecta Unit (%s)", dataTransService.getUnitName()));
            getThing().setProperty(PROPERTY_GW_NAME, dataTransService.getUnitName());

            updateState(CHANNEL_GW_DAYLIGHTSAVINGENABLED, getDaylightSavingTimeEnabled());
            updateState(CHANNEL_GW_FIRMWAREVERSION, getFirmwareVerion());
            updateState(CHANNEL_GW_IS_FIRMWAREUPDATE_SUPPORTED, getIsFirmwareUpdateSupported());
            updateState(CHANNEL_GW_IS_IN_ERROR_STATE, getIsInErrorState());
            updateState(CHANNEL_GW_LED_ENABLED, getIsLedEnabled());
            updateState(CHANNEL_GW_REGION_CODE, getRegionCode());
            updateState(CHANNEL_GW_SERIAL_NUMBER, getSerialNumber());
            updateState(CHANNEL_GW_SSID, getSsid());
            updateState(CHANNEL_GW_TIME_ZONE, getTimeZone());
            updateState(CHANNEL_GW_WIFICONNENTION_SSID, getWifiConnectionSsid());
            updateState(CHANNEL_GW_WIFICONNENTION_STRENGTH, getWifiConnectionStrength());

        } else {
            getThing().setProperty(PROPERTY_GW_NAME, "Unit not registered at Onecta, unitID does not exists.");
        }
    }

    private State getDaylightSavingTimeEnabled() {
        try {
            return OnOffType.from(this.dataTransService.getDaylightSavingTimeEnabled());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getFirmwareVerion() {
        try {
            return new StringType(this.dataTransService.getFirmwareVerion());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getIsFirmwareUpdateSupported() {
        try {
            return OnOffType.from(this.dataTransService.getIsFirmwareUpdateSupported());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getIsInErrorState() {
        try {
            return OnOffType.from(this.dataTransService.getIsInErrorState());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getIsLedEnabled() {
        try {
            return OnOffType.from(this.dataTransService.getIsLedEnabled());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getRegionCode() {
        try {
            return new StringType(this.dataTransService.getRegionCode());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getSerialNumber() {
        try {
            return new StringType(this.dataTransService.getSerialNumber());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getSsid() {
        try {
            return new StringType(this.dataTransService.getSsid());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getTimeZone() {
        try {
            return new StringType(this.dataTransService.getTimeZone());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getWifiConnectionSsid() {
        try {
            return new StringType(this.dataTransService.getWifiConectionSSid());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private State getWifiConnectionStrength() {
        try {
            return new DecimalType(this.dataTransService.getWifiConectionStrength());
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }
}