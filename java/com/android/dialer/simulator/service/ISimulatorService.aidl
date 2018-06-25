package com.android.dialer.simulator.service;

interface ISimulatorService {
 /**
  * Makes an incoming call by simulator api.
  * @param callerId is the number showing on incall UI.
  * @param presentation is one of types of a call e.g. Payphone, Restricted, etc.. check
  * {@link TelecomManager} for more information.
  * */
 void makeIncomingCall(String callerId, int presentation);
 /**
  * Makes an incoming call.
  * @param callerId the number showing on incall UI.
  * @param presentation one of types of a call e.g. Payphone, Restricted, etc.. check
  * {@link TelecomManager} for more information.
  * */
 void makeOutgoingCall(String callerId, int presentation);
 /**
  * Makes an incoming enriched call.
  * Note: simulator mode should be enabled first.
  * */
 void makeIncomingEnrichedCall();
 /**
  * Makes an outgoing enriched call.
  * Note: simulator mode should be enabled first.
  * */
 void makeOutgoingEnrichedCall();
 /**
  * Populate missed call logs.
  * @param num the number of missed call to make with this api.
  * */
 void populateMissedCall(int num);
 /** Populate contacts database to get contacts, call logs, voicemails, etc.. */
 void populateDataBase();
 /** Clean contacts database to clean all exsting contacts, call logs. voicemails, etc.. */
 void cleanDataBase();
 /**
  * Enable simulator mode. After entering simulator mode, all calls made by dialer will be handled
  * by simulator connection service, meaning users can directly make fake calls through simulator.
  * It is also a prerequisite to make an enriched call.
  * */
 void enableSimulatorMode();
 /** Disable simulator mode to use system connection service. */
 void disableSimulatorMode();
}