package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetReadApi;
import com.smartbox.investory.longterm.api.LongTermAssetWriteApi;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface LongTermAssetsClient extends LongTermAssetReadApi, LongTermAssetWriteApi {}
