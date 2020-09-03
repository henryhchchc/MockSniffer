
def _run_core(data):
    from classifier import balance_dataset, calculate_performance
    (project_data, baseline, transform_whole_dataset) = data
    bal = balance_dataset(project_data)
    X = bal.drop(['IS_MOCK', 'TC', 'TM', 'L', 'PROJ'], axis=1)
    y = bal['IS_MOCK']
    y_predict = baseline(
        X) if transform_whole_dataset else X.apply(baseline, axis=1)
    return calculate_performance(y, y_predict)


def pool_core_bal(project, dataset, baseline, transform_whole_dataset=False, iter_count=100):
    from classifier import metrics
    import numpy as np
    import multiprocessing as mp
    p1 = dataset[dataset['PROJ'] == project]
    with mp.Pool() as pl:
        scores = pl.map(_run_core, ((p1, baseline, transform_whole_dataset)
                                    for i in range(iter_count)))
    return project, {
        k: np.mean([s[k] for s in scores])*100
        for k in metrics
    }


def run_baseline(dataset, baseline, transform_whole_dataset=False):
    import pandas as pd
    from dataloader import projects
    runing_result = [pool_core_bal(
        p, dataset, baseline, transform_whole_dataset) for p in projects]
    perf_df = pd.DataFrame([
        {
            'project': proj,
            **perf
        }
        for proj, perf in runing_result
    ]).set_index(['project'])
    return perf_df
