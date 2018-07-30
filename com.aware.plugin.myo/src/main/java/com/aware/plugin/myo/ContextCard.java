package com.aware.plugin.myo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.utils.IContextCard;

public class ContextCard implements IContextCard {

    //Constructor used to instantiate this card
    public ContextCard() {
    }

    @Override
    public View getContextCard(final Context context) {

        //Load card layout
        View card = LayoutInflater.from(context).inflate(R.layout.card, null);

        //Return the card to AWARE/apps
        return card;
    }

}
