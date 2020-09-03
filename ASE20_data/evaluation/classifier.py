from sklearn.model_selection import train_test_split


def balance_dataset(dataset):
    import pandas as pd
    mocked = dataset[dataset['IS_MOCK']]
    not_mocked = dataset[dataset['IS_MOCK'] ==
                         False].sample(n=mocked['TC'].count())
    return pd.concat([mocked, not_mocked])


metrics = ['accuracy', 'precision', 'recall', 'f1-score']


def calculate_performance(y_test, y_predict):
    from sklearn.metrics import precision_score, recall_score, accuracy_score, f1_score
    acc = accuracy_score(y_test, y_predict)
    prec = precision_score(y_test, y_predict)
    recall = recall_score(y_test, y_predict)
    f1 = f1_score(y_test, y_predict)
    return {
        'accuracy': acc,
        'precision': prec,
        'recall': recall,
        'f1-score': f1
    }


def get_model():
    from sklearn.ensemble import GradientBoostingClassifier
    return GradientBoostingClassifier(
        n_estimators=800,
        learning_rate=0.005,
        max_depth=3
    )


def run_classifier(X_train, X_test, y_train, y_test):
    from sklearn.ensemble import GradientBoostingClassifier
    classifier = get_model()
    classifier.fit(X_train, y_train)
    y_predict = classifier.predict(X_test)
    return calculate_performance(y_test, y_predict)
