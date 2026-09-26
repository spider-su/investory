package com.smartbox.investory.ryczalt.application.port;

import com.smartbox.investory.ryczalt.application.RyczaltProfile;

/** Reads the profile-owned taxpayer identity without exposing persistence details. */
public interface RyczaltProfileReader {
  RyczaltProfile read(long profileId);
}
